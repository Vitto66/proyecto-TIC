package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ActivityDateView;
import uy.edu.um.proytic2026.dto.ActivitySchedule;
import uy.edu.um.proytic2026.dto.AddActivityDateRequest;
import uy.edu.um.proytic2026.dto.AddDateResult;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.ActivityNotPublishedException;
import uy.edu.um.proytic2026.exception.DuplicateDateException;
import uy.edu.um.proytic2026.exception.PastDateException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-09 Definir fechas, horarios y cupos de una actividad: reglas de negocio.
 * <p>
 * Precondición: la actividad está publicada y es del prestador. Cada fecha (instancia) dura lo
 * que dura la actividad; dos fechas se superponen si una empieza antes de que termine la otra.
 */
@Service
@RequiredArgsConstructor
public class ActivityDateService {

    /** Estados de reserva que ocupan lugar: la PENDIENTE todavía no (CU-14), la VENCIDA ya no (CU-16). */
    static final Set<ReservationStatus> SEAT_TAKING = Set.of(ReservationStatus.CONFIRMED, ReservationStatus.FINISHED);

    private final ActivityRepository activityRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    /** Paso 1: las fechas de una actividad del prestador, con sus cupos. */
    @Transactional(readOnly = true)
    public ActivitySchedule schedule(Long providerId, Long activityId) {
        Activity activity = ownedActivity(providerId, activityId);
        List<ActivityInstance> instances = instanceRepository.findByActivityIdOrderByStartAtAsc(activityId);
        List<ActivityDateView> views = toViews(activity, instances);

        LocalDateTime now = LocalDateTime.now(clock);
        List<ActivityDateView> upcoming = views.stream().filter(v -> v.startAt().isAfter(now)).toList();
        List<ActivityDateView> past = views.stream().filter(v -> !v.startAt().isAfter(now))
                .sorted(Comparator.comparing(ActivityDateView::startAt).reversed())
                .toList();
        return new ActivitySchedule(activity.getId(), activity.getName(), activity.getDurationMinutes(),
                activity.isActive(), upcoming, past);
    }

    /**
     * Pasos 2 a 4: valida y registra una fecha disponible.
     * Se espera que el request ya haya pasado por las validaciones de las anotaciones.
     *
     * @return la fecha guardada, o las fechas con las que se superpone si falta confirmar (3b).
     * @throws ActivityNotFoundException     la actividad no existe o no es del prestador.
     * @throws ActivityNotPublishedException la actividad está dada de baja.
     * @throws PastDateException             3a: la fecha u hora ya pasaron.
     * @throws DuplicateDateException        ya hay una fecha de esta actividad a esa misma hora.
     */
    @Transactional
    public AddDateResult addDate(Long providerId, Long activityId, AddActivityDateRequest request) {
        Activity activity = ownedActivity(providerId, activityId);
        if (!activity.isActive()) {
            throw new ActivityNotPublishedException();
        }

        LocalDateTime start = request.startAt();
        // 3a. Fecha u hora ya pasadas
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new PastDateException();
        }
        if (instanceRepository.existsByActivityIdAndStartAt(activityId, start)) {
            throw new DuplicateDateException();
        }

        // 3b. Superposición con otra instancia de la misma actividad → se pide confirmación
        if (!request.isConfirmOverlap()) {
            List<ActivityInstance> overlapping = overlapping(activity, start);
            if (!overlapping.isEmpty()) {
                return AddDateResult.needsConfirmation(toViews(activity, overlapping));
            }
        }

        ActivityInstance instance = ActivityInstance.builder()
                .activity(activity)
                .startAt(start)
                .maxCapacity(request.getMaxCapacity())
                .build();
        try {
            // Si dos pedidos iguales llegan a la vez, la restricción UNIQUE salta acá
            instance = instanceRepository.saveAndFlush(instance);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateDateException();
        }
        return AddDateResult.saved(toView(activity, instance, 0));
    }

    /** Las fechas de la actividad que se pisan con una que empieza en start. */
    private List<ActivityInstance> overlapping(Activity activity, LocalDateTime start) {
        long duration = activity.getDurationMinutes();
        return instanceRepository.findByActivityIdAndStartAtBetweenOrderByStartAtAsc(
                        activity.getId(), start.minusMinutes(duration), start.plusMinutes(duration)).stream()
                .filter(other -> Math.abs(Duration.between(other.getStartAt(), start).toMinutes()) < duration)
                .toList();
    }

    private Activity ownedActivity(Long providerId, Long activityId) {
        return activityRepository.findById(activityId)
                .filter(a -> a.getBusiness().getProvider().getId().equals(providerId))
                .orElseThrow(ActivityNotFoundException::new);
    }

    private List<ActivityDateView> toViews(Activity activity, List<ActivityInstance> instances) {
        Map<Long, Long> occupied = occupiedSeats(instances.stream().map(ActivityInstance::getId).toList());
        return instances.stream()
                .map(i -> toView(activity, i, occupied.getOrDefault(i.getId(), 0L)))
                .toList();
    }

    private Map<Long, Long> occupiedSeats(Collection<Long> instanceIds) {
        if (instanceIds.isEmpty()) return Map.of();
        return reservationRepository.occupiedSeats(instanceIds, SEAT_TAKING).stream()
                .collect(Collectors.toMap(ReservationRepository.OccupiedSeats::getInstanceId,
                        ReservationRepository.OccupiedSeats::getSeats));
    }

    private static ActivityDateView toView(Activity activity, ActivityInstance instance, long occupied) {
        return new ActivityDateView(instance.getId(), instance.getStartAt(),
                instance.getStartAt().plusMinutes(activity.getDurationMinutes()),
                instance.getMaxCapacity(), occupied);
    }
}
