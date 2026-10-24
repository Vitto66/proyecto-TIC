package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ActivityDateView;
import uy.edu.um.proytic2026.dto.ActivityDetail;
import uy.edu.um.proytic2026.dto.DailyForecast;
import uy.edu.um.proytic2026.dto.DetailDate;
import uy.edu.um.proytic2026.dto.ReviewView;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityEquipment;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CU-06 Consultar el detalle de una actividad.
 * <p>
 * Se arma en dos pasos: {@link #load} lee todo de la base dentro de una transacción, y
 * {@link #withForecast} agrega el clima afuera, para no tener una conexión de la base ocupada
 * mientras se espera a la API (el plan gratuito de Supabase tiene pocas).
 */
@Service
@RequiredArgsConstructor
public class ActivityDetailService {

    /** Open-Meteo pronostica hasta 16 días, contando hoy. */
    static final int FORECAST_DAYS = 16;

    private final ActivityRepository activityRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final WeatherService weatherService;
    private final Clock clock;

    /** Pasos 2 a 4. Vacío si la actividad no existe o no está publicada (precondición). */
    @Transactional(readOnly = true)
    public Optional<ActivityDetail> load(Long activityId) {
        Optional<Activity> found = activityRepository.findById(activityId).filter(Activity::isActive);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Activity a = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        // Paso 3: próximas fechas con su disponibilidad
        List<ActivityInstance> upcoming = instanceRepository.findUpcoming(List.of(activityId), now);
        Map<Long, Long> occupied = upcoming.isEmpty() ? Map.of()
                : reservationRepository.occupiedSeats(upcoming.stream().map(ActivityInstance::getId).toList(),
                        ActivityDateService.SEAT_TAKING).stream()
                .collect(Collectors.toMap(ReservationRepository.OccupiedSeats::getInstanceId,
                        ReservationRepository.OccupiedSeats::getSeats));
        List<DetailDate> dates = upcoming.stream().map(i -> {
            ActivityDateView view = new ActivityDateView(i.getId(), i.getStartAt(),
                    i.getStartAt().plusMinutes(a.getDurationMinutes()), i.getMaxCapacity(),
                    occupied.getOrDefault(i.getId(), 0L));
            // Las reservas cierran X minutos antes del inicio (CU-08 paso 5, CU-14 4b)
            boolean closed = !now.isBefore(i.getStartAt().minusMinutes(a.getBookingCutoffMinutes()));
            return new DetailDate(view, closed, null);
        }).toList();

        // Paso 4: comentarios y calificaciones
        List<ReviewView> reviews = reviewRepository.findByActivityId(activityId).stream()
                .map(r -> new ReviewView(author(r.getReservation().getTourist()), r.getRating(),
                        r.getComment(), r.getCreatedAt()))
                .toList();
        Double average = reviews.isEmpty() ? null
                : reviews.stream().mapToInt(ReviewView::rating).average().orElseThrow();

        // Paso 2: equipamiento necesario (lo trae el turista) y provisto (lo pone el prestador)
        List<ActivityEquipment> equipment = a.getEquipment().stream()
                .sorted(Comparator.comparing(e -> e.getEquipmentType().getName()))
                .toList();

        return Optional.of(new ActivityDetail(a.getId(), a.getName(), a.getDescription(), a.getBusiness().getName(),
                a.getLocality().getName(), a.getLocality().getDepartment().getName(),
                a.getTypes().stream().map(ActivityType::getName).sorted().toList(),
                a.getDurationMinutes(), a.getPrice(), a.getMinAge(), a.getBookingCutoffMinutes(),
                equipment.stream().filter(ActivityEquipment::isProvidedByProvider)
                        .map(e -> e.getEquipmentType().getName()).toList(),
                equipment.stream().filter(e -> !e.isProvidedByProvider())
                        .map(e -> e.getEquipmentType().getName()).toList(),
                dates, reviews, average));
    }

    /**
     * Paso 5: el pronóstico para el lugar y las fechas que entran en el plazo de la API. Si no
     * está disponible (fecha demasiado lejana o la API no responde), las fechas quedan sin clima (5a).
     */
    public ActivityDetail withForecast(ActivityDetail detail) {
        LocalDate lastForecastDay = LocalDate.now(clock).plusDays(FORECAST_DAYS - 1);
        boolean anyInRange = detail.dates().stream()
                .anyMatch(d -> !d.date().startAt().toLocalDate().isAfter(lastForecastDay));
        if (!anyInRange) {
            return detail; // Ni se llama a la API
        }
        Map<LocalDate, DailyForecast> forecast = weatherService.forecast(detail.localityName(), detail.departmentName());
        return detail.withDates(detail.dates().stream()
                .map(d -> d.withForecast(forecast.get(d.date().startAt().toLocalDate())))
                .toList());
    }

    /** "Ana P.": el nombre y la inicial del apellido. */
    private static String author(Tourist tourist) {
        String last = tourist.getLastName();
        return tourist.getFirstName() + (last == null || last.isBlank() ? "" : " " + last.charAt(0) + ".");
    }
}
