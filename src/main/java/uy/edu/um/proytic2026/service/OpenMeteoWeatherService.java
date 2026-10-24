package uy.edu.um.proytic2026.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import uy.edu.um.proytic2026.dto.DailyForecast;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pronóstico del clima con Open-Meteo (https://open-meteo.com), una API pública que no pide
 * clave para uso no comercial.
 * <p>
 * La tabla locality no tiene coordenadas, así que primero se busca la localidad por nombre en
 * Uruguay (geocoding) y después se pide el pronóstico diario de los próximos 16 días, que es el
 * máximo de la API. Para no llamarla en cada visita, las coordenadas se guardan en memoria y el
 * pronóstico se reutiliza durante una hora.
 * <p>
 * Si algo falla (sin red, la API no responde, no encuentra el lugar) se devuelve un pronóstico
 * vacío y se deja constancia en el log: el detalle de la actividad se muestra igual (CU-06 5a).
 */
@Slf4j
@Service
public class OpenMeteoWeatherService implements WeatherService {

    static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
            + "?name={name}&count=10&language=es&format=json&countryCode=UY";
    static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude={lat}&longitude={lon}"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
            + "&timezone={timezone}&forecast_days=16";
    static final Duration FORECAST_TTL = Duration.ofHours(1);

    private final RestClient restClient;
    private final Clock clock;

    /** Coordenadas por "localidad|departamento". Optional.empty() = la API no la encontró. */
    private final Map<String, Optional<Coordinates>> places = new ConcurrentHashMap<>();
    private final Map<Coordinates, CachedForecast> forecasts = new ConcurrentHashMap<>();

    record Coordinates(double latitude, double longitude) {
    }

    private record CachedForecast(Instant fetchedAt, Map<LocalDate, DailyForecast> days) {
    }

    @Autowired
    public OpenMeteoWeatherService(Clock clock) {
        this(defaultBuilder(), clock);
    }

    /** Para los tests, que conectan un servidor simulado al builder. */
    OpenMeteoWeatherService(RestClient.Builder builder, Clock clock) {
        this.restClient = builder.build();
        this.clock = clock;
    }

    /** Tiempos de espera cortos: si la API tarda, la página se muestra sin clima en vez de colgarse. */
    private static RestClient.Builder defaultBuilder() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public Map<LocalDate, DailyForecast> forecast(String locality, String department) {
        try {
            Optional<Coordinates> place = places.computeIfAbsent(key(locality, department),
                    k -> lookUp(locality, department));
            if (place.isEmpty()) {
                return Map.of();
            }
            CachedForecast cached = forecasts.get(place.get());
            Instant now = clock.instant();
            if (cached == null || cached.fetchedAt().plus(FORECAST_TTL).isBefore(now)) {
                cached = new CachedForecast(now, fetchForecast(place.get()));
                forecasts.put(place.get(), cached);
            }
            return cached.days();
        } catch (RuntimeException ex) {
            // 5a. Pronóstico no disponible: el detalle se muestra sin el clima
            log.warn("No se pudo obtener el pronóstico de {} ({}): {}", locality, department, ex.toString());
            return Map.of();
        }
    }

    /** Busca la localidad en Uruguay. Si la API falla lanza la excepción, así no queda guardado como "no existe". */
    @SuppressWarnings("unchecked")
    private Optional<Coordinates> lookUp(String locality, String department) {
        Map<String, Object> body = restClient.get().uri(GEOCODING_URL, locality).retrieve().body(Map.class);
        return pickPlace(body, department);
    }

    @SuppressWarnings("unchecked")
    private Map<LocalDate, DailyForecast> fetchForecast(Coordinates c) {
        Map<String, Object> body = restClient.get()
                .uri(FORECAST_URL, c.latitude(), c.longitude(), "America/Montevideo")
                .retrieve().body(Map.class);
        return parseForecast(body);
    }

    /**
     * De los resultados del geocoding, el del departamento de la actividad (admin1); si ninguno
     * coincide, el primero. Hay localidades con el mismo nombre en varios departamentos.
     */
    @SuppressWarnings("unchecked")
    static Optional<Coordinates> pickPlace(Map<String, Object> body, String department) {
        if (body == null || !(body.get("results") instanceof List<?> results) || results.isEmpty()) {
            return Optional.empty();
        }
        String wanted = CatalogService.normalize(department);
        Map<String, Object> chosen = results.stream()
                .map(r -> (Map<String, Object>) r)
                .filter(r -> r.get("admin1") != null && CatalogService.normalize(r.get("admin1").toString()).equals(wanted))
                .findFirst()
                .orElse((Map<String, Object>) results.get(0));
        return Optional.of(new Coordinates(((Number) chosen.get("latitude")).doubleValue(),
                ((Number) chosen.get("longitude")).doubleValue()));
    }

    /** Convierte la respuesta diaria de Open-Meteo en un pronóstico por día. */
    @SuppressWarnings("unchecked")
    static Map<LocalDate, DailyForecast> parseForecast(Map<String, Object> body) {
        if (body == null || !(body.get("daily") instanceof Map<?, ?> rawDaily)) {
            return Map.of();
        }
        Map<String, Object> daily = (Map<String, Object>) rawDaily;
        List<Object> time = (List<Object>) daily.get("time");
        List<Object> codes = (List<Object>) daily.get("weather_code");
        List<Object> max = (List<Object>) daily.get("temperature_2m_max");
        List<Object> min = (List<Object>) daily.get("temperature_2m_min");
        List<Object> rain = (List<Object>) daily.getOrDefault("precipitation_probability_max", List.of());
        if (time == null || codes == null || max == null || min == null) {
            return Map.of();
        }

        Map<LocalDate, DailyForecast> result = new LinkedHashMap<>();
        for (int i = 0; i < time.size(); i++) {
            // Un día sin datos (null) se saltea: ese día se muestra sin clima
            if (codes.get(i) == null || max.get(i) == null || min.get(i) == null) continue;
            LocalDate date = LocalDate.parse(time.get(i).toString());
            Integer probability = i < rain.size() && rain.get(i) instanceof Number n ? n.intValue() : null;
            result.put(date, new DailyForecast(date, ((Number) codes.get(i)).intValue(),
                    ((Number) max.get(i)).doubleValue(), ((Number) min.get(i)).doubleValue(), probability));
        }
        return result;
    }

    private static String key(String locality, String department) {
        return CatalogService.normalize(locality) + "|" + CatalogService.normalize(department);
    }
}
