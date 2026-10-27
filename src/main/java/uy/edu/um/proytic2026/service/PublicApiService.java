package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ActivityOfDay;
import uy.edu.um.proytic2026.dto.PublicBusiness;
import uy.edu.um.proytic2026.dto.TopActivity;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.InvalidApiParameterException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consultas de la API para otras plataformas (RF-10). Son todas de solo lectura y no
 * necesitan un usuario de la plataforma.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicApiService {

    private final BusinessRepository businessRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final DepartmentRepository departmentRepository;
    private final LocalityRepository localityRepository;
    private final ReviewRepository reviewRepository;
    private final ActivityRepository activityRepository;

    /** Cuántas actividades devuelve el top del mes (CU-21). */
    static final int TOP_SIZE = 3;

    /**
     * CU-19: todos los emprendimientos de prestadores aprobados. Los de prestadores pendientes
     * o rechazados no se publican. Si no hay ninguno, la lista vuelve vacía (2a).
     */
    public List<PublicBusiness> listBusinesses() {
        return businessRepository.findAllByProviderStatus(ProviderStatus.APPROVED).stream()
                .map(PublicBusiness::from)
                .toList();
    }

    /**
     * CU-20: las actividades publicadas que tienen fechas ese día en un departamento o en una
     * localidad (hay que indicar uno de los dos). Cada actividad trae sus horarios de ese día con
     * los cupos libres. Ordenadas por su primer horario. Sin actividades → lista vacía (3a).
     *
     * @throws InvalidApiParameterException si no se indica ubicación, se indican las dos, o no
     *                                      existe el departamento o la localidad (2a)
     */
    public List<ActivityOfDay> activitiesOfDay(LocalDate date, Integer departmentId, Integer localityId) {
        if (departmentId == null && localityId == null) {
            throw new InvalidApiParameterException("departmentId",
                    "Hay que indicar un departamento (departmentId) o una localidad (localityId)");
        }
        if (departmentId != null && localityId != null) {
            throw new InvalidApiParameterException("localityId",
                    "Indicar un departamento o una localidad, no los dos");
        }

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        List<ActivityInstance> instances;
        if (departmentId != null) {
            if (!departmentRepository.existsById(departmentId)) {
                throw new InvalidApiParameterException("departmentId", "No existe el departamento " + departmentId);
            }
            instances = instanceRepository.findPublishedInDepartment(departmentId, from, to);
        } else {
            if (!localityRepository.existsById(localityId)) {
                throw new InvalidApiParameterException("localityId", "No existe la localidad " + localityId);
            }
            instances = instanceRepository.findPublishedInLocality(localityId, from, to);
        }
        if (instances.isEmpty()) {
            return List.of();
        }

        // Lugares ocupados por las reservas confirmadas o finalizadas de esas fechas
        Map<Long, Long> occupied = reservationRepository
                .occupiedSeats(instances.stream().map(ActivityInstance::getId).toList(),
                        ActivityDateService.SEAT_TAKING).stream()
                .collect(Collectors.toMap(ReservationRepository.OccupiedSeats::getInstanceId,
                        ReservationRepository.OccupiedSeats::getSeats));

        // Agrupa los horarios por actividad. Las instancias vienen ordenadas por hora, así que
        // las actividades quedan ordenadas por su primer horario del día.
        Map<Activity, List<ActivityOfDay.Time>> byActivity = new LinkedHashMap<>();
        for (ActivityInstance i : instances) {
            Activity a = i.getActivity();
            byActivity.computeIfAbsent(a, key -> new ArrayList<>()).add(new ActivityOfDay.Time(
                    i.getId(), i.getStartAt(), i.getStartAt().plusMinutes(a.getDurationMinutes()),
                    availableSeats(i.getMaxCapacity(), occupied.getOrDefault(i.getId(), 0L))));
        }
        return byActivity.entrySet().stream().map(e -> {
            Activity a = e.getKey();
            return new ActivityOfDay(a.getId(), a.getName(), a.getBusiness().getName(),
                    a.getLocality().getName(), a.getLocality().getDepartment().getName(),
                    a.getPrice(), a.getDurationMinutes(), a.getMinAge(),
                    a.getTypes().stream().map(ActivityType::getName).sorted().toList(),
                    e.getValue());
        }).toList();
    }

    /** null si no hay límite de cupos; nunca negativo. */
    private static Integer availableSeats(Integer maxCapacity, long occupied) {
        return maxCapacity == null ? null : (int) Math.max(0, maxCapacity - occupied);
    }

    /**
     * CU-21: las tres actividades publicadas de un departamento mejor puntuadas con las
     * valoraciones recibidas en ese mes. Si hay menos de tres valoradas, se devuelven las que
     * haya (3a); si empatan en el promedio, primero la que recibió más valoraciones (3b).
     *
     * @param month el mes en formato yyyy-MM, por ejemplo 2026-10
     * @throws InvalidApiParameterException si el mes tiene un formato inválido o el departamento
     *                                      no existe (1a)
     */
    public List<TopActivity> topActivitiesOfMonth(Integer departmentId, String month) {
        YearMonth yearMonth = parseMonth(month);
        if (!departmentRepository.existsById(departmentId)) {
            throw new InvalidApiParameterException("departmentId", "No existe el departamento " + departmentId);
        }

        List<ReviewRepository.RatingStats> top = reviewRepository.ratingStatsInDepartment(departmentId,
                        yearMonth.atDay(1).atStartOfDay(), yearMonth.plusMonths(1).atDay(1).atStartOfDay())
                .stream().limit(TOP_SIZE).toList();
        if (top.isEmpty()) {
            return List.of();
        }

        Map<Long, Activity> activities = activityRepository
                .findAllById(top.stream().map(ReviewRepository.RatingStats::getActivityId).toList())
                .stream().collect(Collectors.toMap(Activity::getId, Function.identity()));
        List<TopActivity> result = new ArrayList<>();
        for (ReviewRepository.RatingStats stats : top) {
            Activity a = activities.get(stats.getActivityId());
            result.add(new TopActivity(result.size() + 1, a.getId(), a.getName(), a.getBusiness().getName(),
                    a.getLocality().getName(), a.getLocality().getDepartment().getName(),
                    round2(stats.getAverage()), stats.getCount()));
        }
        return result;
    }

    /** "2026-10" → octubre de 2026. Otro formato → error indicando el parámetro (1a). */
    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidApiParameterException("month",
                    "El mes tiene que tener el formato yyyy-MM, por ejemplo 2026-10");
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
