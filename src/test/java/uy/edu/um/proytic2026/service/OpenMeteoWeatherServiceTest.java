package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.DailyForecast;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests del pronóstico con Open-Meteo (CU-06 paso 5 y 5a), con un servidor simulado: no salen
 * a internet. Las respuestas siguen el formato de la documentación de la API.
 */
class OpenMeteoWeatherServiceTest {

    private static final String GEOCODING = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast";

    /** Hay una La Paloma en Durazno y otra en Rocha: tiene que elegir la del departamento. */
    private static final String GEOCODING_JSON = """
            {"results":[
              {"id":1,"name":"La Paloma","latitude":-32.73,"longitude":-55.58,"country_code":"UY","admin1":"Durazno"},
              {"id":2,"name":"La Paloma","latitude":-34.66,"longitude":-54.16,"country_code":"UY","admin1":"Rocha"}
            ],"generationtime_ms":0.5}
            """;

    private static final String FORECAST_JSON = """
            {"latitude":-34.66,"longitude":-54.16,"timezone":"America/Montevideo",
             "daily_units":{"time":"iso8601","weather_code":"wmo code","temperature_2m_max":"°C"},
             "daily":{
               "time":["2026-10-25","2026-10-26","2026-10-27"],
               "weather_code":[2,61,null],
               "temperature_2m_max":[24.4,18.0,null],
               "temperature_2m_min":[15.2,12.6,null],
               "precipitation_probability_max":[10,null,null]
             }}
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OpenMeteoWeatherService service;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 10, 24, 12, 0).atZone(ClockConfig.URUGUAY).toInstant(),
                ClockConfig.URUGUAY);
        service = new OpenMeteoWeatherService(builder, clock);
    }

    @Test
    void forecast_findsTheLocalityInItsDepartmentAndParsesEachDay() {
        server.expect(requestTo(allOf(startsWith(GEOCODING), containsString("name=La%20Paloma"),
                        containsString("countryCode=UY"))))
                .andRespond(withSuccess(GEOCODING_JSON, MediaType.APPLICATION_JSON));
        // Las coordenadas de la La Paloma de Rocha, no la de Durazno
        server.expect(requestTo(allOf(startsWith(FORECAST), containsString("latitude=-34.66"),
                        containsString("longitude=-54.16"), containsString("forecast_days=16"))))
                .andRespond(withSuccess(FORECAST_JSON, MediaType.APPLICATION_JSON));

        Map<LocalDate, DailyForecast> forecast = service.forecast("La Paloma", "Rocha");

        server.verify();
        DailyForecast sunday = forecast.get(LocalDate.of(2026, 10, 25));
        assertThat(sunday.description()).isEqualTo("Parcialmente nublado");
        assertThat(sunday.temperatureLabel()).isEqualTo("24° / 15°");
        assertThat(sunday.rainLabel()).isEqualTo("10 % de lluvia");
        // Un día sin probabilidad de lluvia se muestra igual, sin ese dato
        assertThat(forecast.get(LocalDate.of(2026, 10, 26)).rainLabel()).isNull();
        assertThat(forecast.get(LocalDate.of(2026, 10, 26)).description()).isEqualTo("Lluvia débil");
        // Un día sin datos se saltea
        assertThat(forecast).doesNotContainKey(LocalDate.of(2026, 10, 27));
    }

    @Test
    void forecast_isReusedInsteadOfCallingTheApiOnEveryVisit() {
        server.expect(ExpectedCount.once(), requestTo(startsWith(GEOCODING)))
                .andRespond(withSuccess(GEOCODING_JSON, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(startsWith(FORECAST)))
                .andRespond(withSuccess(FORECAST_JSON, MediaType.APPLICATION_JSON));

        service.forecast("La Paloma", "Rocha");
        Map<LocalDate, DailyForecast> second = service.forecast("la paloma", "ROCHA");

        server.verify();
        assertThat(second).isNotEmpty();
    }

    @Test
    void apiError_returnsEmptyForecastInsteadOfFailing() {
        // 5a. La API no responde bien → el detalle se muestra sin clima
        server.expect(requestTo(startsWith(GEOCODING)))
                .andRespond(withSuccess(GEOCODING_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(FORECAST))).andRespond(withServerError());

        assertThat(service.forecast("La Paloma", "Rocha")).isEmpty();
    }

    @Test
    void geocodingError_returnsEmptyAndIsRetriedNextTime() {
        server.expect(requestTo(startsWith(GEOCODING))).andRespond(withServerError());
        server.expect(requestTo(startsWith(GEOCODING)))
                .andRespond(withSuccess(GEOCODING_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(FORECAST)))
                .andRespond(withSuccess(FORECAST_JSON, MediaType.APPLICATION_JSON));

        assertThat(service.forecast("La Paloma", "Rocha")).isEmpty();
        // La falla no quedó guardada como "no existe": la segunda vez lo vuelve a buscar
        assertThat(service.forecast("La Paloma", "Rocha")).isNotEmpty();
        server.verify();
    }

    @Test
    void unknownPlace_returnsEmptyWithoutAskingForTheForecast() {
        server.expect(requestTo(startsWith(GEOCODING)))
                .andRespond(withSuccess("{\"generationtime_ms\":0.3}", MediaType.APPLICATION_JSON));

        assertThat(service.forecast("Lugar Inventado", "Rocha")).isEmpty();
        server.verify();
    }

    @Test
    void pickPlace_withoutDepartmentMatch_usesTheFirstResult() {
        Map<String, Object> body = Map.of("results", java.util.List.of(
                Map.of("latitude", -30.0, "longitude", -56.0, "admin1", "Artigas")));

        assertThat(OpenMeteoWeatherService.pickPlace(body, "Rocha"))
                .hasValueSatisfying(c -> assertThat(c.latitude()).isEqualTo(-30.0));
    }

    @Test
    void weatherDescriptionsAndIcons() {
        LocalDate day = LocalDate.of(2026, 10, 25);
        assertThat(new DailyForecast(day, 0, 20, 10, null).description()).isEqualTo("Despejado");
        assertThat(new DailyForecast(day, 0, 20, 10, null).icon()).isEqualTo("☀️");
        assertThat(new DailyForecast(day, 95, 20, 10, null).description()).isEqualTo("Tormenta");
        assertThat(new DailyForecast(day, 45, 20, 10, null).description()).isEqualTo("Niebla");
        assertThat(new DailyForecast(day, 1234, 20, 10, null).description()).isEqualTo("Sin datos");
    }
}
