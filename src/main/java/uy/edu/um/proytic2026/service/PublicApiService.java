package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ActivityOfDay;
import uy.edu.um.proytic2026.dto.PublicBusiness;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.InvalidApiParameterException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
}
