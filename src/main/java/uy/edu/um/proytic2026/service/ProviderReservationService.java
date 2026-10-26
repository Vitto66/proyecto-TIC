package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.*;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.exception.InstanceNotFoundException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-11 Consultar las reservas de sus actividades.
 * <p>
 * El prestador ve las fechas de todas sus actividades con los cupos ocupados y libres, y al
 * elegir una, los turistas anotados con la cantidad de personas y el estado de cada reserva.
 * Es solo consulta: no cambia nada.
 */
@Service
@RequiredArgsConstructor
public class ProviderReservationService {

    /** Las que ocupan lugar (CU-14, CU-15). Las mismas que usa el CU-09 para los cupos. */
    static final Set<ReservationStatus> SEAT_TAKING = Set.of(ReservationStatus.CONFIRMED, ReservationStatus.FINISHED);
    /** Las que se cuentan como reservas recibidas: todas menos las vencidas. */
    static final Set<ReservationStatus> CURRENT = Set.of(
            ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.FINISHED);

    private final ActivityInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    /** Paso 2: las fechas con su fecha, hora, cupos ocupados y libres, y cuántas reservas tienen. */
    @Transactional(readOnly = true)
    public ProviderReservations overview(Long providerId) {
        List<ActivityInstance> instances = instanceRepository.findAllByProviderId(providerId);
        List<Long> ids = instances.stream().map(ActivityInstance::getId).toList();

        Map<Long, Long> seats = ids.isEmpty() ? Map.of()
                : reservationRepository.occupiedSeats(ids, SEAT_TAKING).stream()
                .collect(Collectors.toMap(ReservationRepository.OccupiedSeats::getInstanceId,
                        ReservationRepository.OccupiedSeats::getSeats));
        Map<Long, Long> counts = ids.isEmpty() ? Map.of()
                : reservationRepository.countByInstance(ids, CURRENT).stream()
                .collect(Collectors.toMap(ReservationRepository.ReservationCount::getInstanceId,
                        ReservationRepository.ReservationCount::getReservations));

        List<ProviderDateRow> rows = instances.stream()
                .map(i -> new ProviderDateRow(i.getActivity().getId(), i.getActivity().getName(),
                        toView(i, seats.getOrDefault(i.getId(), 0L)), counts.getOrDefault(i.getId(), 0L)))
                .toList();

        LocalDateTime now = LocalDateTime.now(clock);
        List<ProviderDateRow> upcoming = rows.stream().filter(r -> r.date().endAt().isAfter(now)).toList();
        List<ProviderDateRow> past = rows.stream().filter(r -> !r.date().endAt().isAfter(now))
                .sorted(Comparator.comparing((ProviderDateRow r) -> r.date().startAt()).reversed())
                .toList();
        long total = rows.stream().mapToLong(ProviderDateRow::reservations).sum();
        return new ProviderReservations(upcoming, past, total);
    }

    /**
     * Pasos 3 y 4: las reservas de una fecha. Incluye las vencidas, para que el prestador vea
     * también quién reservó y no pagó.
     *
     * @throws InstanceNotFoundException la fecha no existe o es de otro prestador.
     */
    @Transactional(readOnly = true)
    public InstanceReservations instance(Long providerId, Long instanceId) {
        ActivityInstance instance = instanceRepository.findById(instanceId)
                .filter(i -> i.getActivity().getBusiness().getProvider().getId().equals(providerId))
                .orElseThrow(InstanceNotFoundException::new);

        List<Reservation> reservations = reservationRepository.findAllForInstance(instanceId);
        long occupied = reservations.stream()
                .filter(r -> SEAT_TAKING.contains(r.getStatus()))
                .mapToLong(Reservation::getParticipants).sum();

        List<ProviderReservationRow> rows = reservations.stream().map(ProviderReservationService::toRow).toList();
        Activity activity = instance.getActivity();
        return new InstanceReservations(activity.getId(), activity.getName(), toView(instance, occupied), rows);
    }

    private static ProviderReservationRow toRow(Reservation r) {
        Tourist t = r.getTourist();
        List<CompanionView> companions = r.getCompanions().stream()
                .map(c -> new CompanionView(c.getPosition(), c.getFirstName(), c.getLastName(),
                        c.getNationalId(), c.getPhone()))
                .toList();
        return new ProviderReservationRow(r.getId(), t.getFirstName() + " " + t.getLastName(), t.getNationalId(),
                t.getUser().getEmail(), t.getUser().getPhone(), r.getParticipants(), r.getTotalPrice(),
                r.getStatus(), companions);
    }

    private static ActivityDateView toView(ActivityInstance i, long occupied) {
        return new ActivityDateView(i.getId(), i.getStartAt(),
                i.getStartAt().plusMinutes(i.getActivity().getDurationMinutes()), i.getMaxCapacity(), occupied);
    }
}
