package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ActivityDateView;
import uy.edu.um.proytic2026.dto.CompanionForm;
import uy.edu.um.proytic2026.dto.CompanionView;
import uy.edu.um.proytic2026.dto.DetailDate;
import uy.edu.um.proytic2026.dto.ReservationOptions;
import uy.edu.um.proytic2026.dto.ReservationQuote;
import uy.edu.um.proytic2026.dto.ReservationView;
import uy.edu.um.proytic2026.dto.ReserveRequest;
import uy.edu.um.proytic2026.dto.TouristSummary;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.PaymentStatus;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationParticipant;
import uy.edu.um.proytic2026.entity.ReservationPayment;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.BookingClosedException;
import uy.edu.um.proytic2026.exception.DateNotAvailableException;
import uy.edu.um.proytic2026.exception.NotEnoughSeatsException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ReservationPaymentRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-14 Reservar una actividad: reglas de negocio.
 * <p>
 * La reserva se crea PENDIENTE y todavía no ocupa cupos: los descuenta el pago aprobado
 * (CU-15). Por eso las verificaciones del paso 4 (cupos y límite de minutos previos) se
 * repiten al confirmar, y se vuelven a hacer al pagar.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ActivityRepository activityRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final TouristRepository touristRepository;
    private final ReservationPaymentRepository paymentRepository;
    private final Clock clock;

    /** Lo que se puede elegir: la actividad publicada y sus próximas fechas (paso 2). */
    @Transactional(readOnly = true)
    public ReservationOptions options(Long activityId) {
        Activity activity = publishedActivity(activityId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ActivityInstance> upcoming = instanceRepository.findUpcoming(List.of(activityId), now);
        Map<Long, Long> occupied = occupiedSeats(upcoming.stream().map(ActivityInstance::getId).toList());
        List<DetailDate> dates = upcoming.stream()
                .map(i -> new DetailDate(view(activity, i, occupied.getOrDefault(i.getId(), 0L)),
                        bookingClosed(activity, i, now), null))
                .toList();
        return new ReservationOptions(activity.getId(), activity.getName(), activity.getBusiness().getName(),
                location(activity), activity.getPrice(), dates);
    }

    /**
     * Pasos 4 y 5: verifica cupos y límite de minutos y arma el resumen con el precio total.
     *
     * @throws ActivityNotFoundException  la actividad no existe o no está publicada.
     * @throws DateNotAvailableException  la fecha no es de esa actividad o ya pasó.
     * @throws BookingClosedException     4b: se superó el límite de minutos previos.
     * @throws NotEnoughSeatsException    4a: no hay cupos suficientes.
     */
    @Transactional(readOnly = true)
    public ReservationQuote quote(Long activityId, ReserveRequest request) {
        return check(activityId, request).quote();
    }

    /**
     * Paso 6: el turista confirma. Se vuelve a verificar todo y se crea la reserva PENDIENTE,
     * a la espera del pago (CU-15). El precio total queda fijo: un cambio de precio posterior no
     * la afecta (CU-10).
     */
    @Transactional
    public ReservationView reserve(Long touristId, Long activityId, ReserveRequest request) {
        Checked checked = check(activityId, request);
        Tourist tourist = touristRepository.findById(touristId).orElseThrow(ReservationNotFoundException::new);
        ReservationQuote quote = checked.quote();
        // El controlador ya los validó; esto evita guardar una reserva con datos incompletos igual
        if (!request.companionsMatchParticipants() || !repeatedNationalIds(tourist, request).isEmpty()) {
            throw new IllegalArgumentException("Faltan los datos de algún acompañante o hay cédulas repetidas");
        }

        Reservation reservation = reservationRepository.save(Reservation.builder()
                .tourist(tourist)
                .instance(checked.instance())
                .participants(quote.participants())
                .totalPrice(quote.total())
                .status(ReservationStatus.PENDING)
                .createdAt(LocalDateTime.now(clock))
                .build());
        for (CompanionForm c : request.getCompanions()) {
            CompanionForm data = c.trimmed();
            reservation.addCompanion(ReservationParticipant.builder().firstName(data.getFirstName())
                    .lastName(data.getLastName()).nationalId(data.getNationalId()).phone(data.getPhone()).build());
        }
        return toView(reservation, checked.activity(), quote.date(), null);
    }

    /** El turista que reserva, que es la persona 1. */
    @Transactional(readOnly = true)
    public TouristSummary touristSummary(Long touristId) {
        Tourist tourist = touristRepository.findById(touristId).orElseThrow(ReservationNotFoundException::new);
        return new TouristSummary(tourist.getFirstName() + " " + tourist.getLastName(), tourist.getNationalId());
    }

    /**
     * Los acompañantes (por posición en la lista) cuya cédula ya está en la reserva: la del turista
     * o la de un acompañante anterior. Una misma persona no puede ir dos veces.
     */
    @Transactional(readOnly = true)
    public List<Integer> repeatedNationalIds(Long touristId, ReserveRequest request) {
        Tourist tourist = touristRepository.findById(touristId).orElseThrow(ReservationNotFoundException::new);
        return repeatedNationalIds(tourist, request);
    }

    private static List<Integer> repeatedNationalIds(Tourist tourist, ReserveRequest request) {
        Set<String> seen = new HashSet<>();
        seen.add(tourist.getNationalId());
        List<Integer> repeated = new ArrayList<>();
        List<CompanionForm> companions = request.getCompanions();
        for (int i = 0; i < companions.size(); i++) {
            String id = companions.get(i) == null ? null : companions.get(i).trimmed().getNationalId();
            if (id != null && !id.isEmpty() && !seen.add(id)) {
                repeated.add(i);
            }
        }
        return repeated;
    }

    /** Una reserva del turista. Si no existe o es de otro turista, no se encuentra. */
    @Transactional(readOnly = true)
    public ReservationView reservation(Long touristId, Long reservationId) {
        Reservation r = reservationRepository.findById(reservationId)
                .filter(found -> found.getTourist().getId().equals(touristId))
                .orElseThrow(ReservationNotFoundException::new);
        ActivityInstance instance = r.getInstance();
        Activity activity = instance.getActivity();
        long occupied = occupiedSeats(List.of(instance.getId())).getOrDefault(instance.getId(), 0L);
        PaymentStatus lastPayment = paymentRepository.findFirstByReservationIdOrderByIdDesc(r.getId())
                .map(ReservationPayment::getStatus).orElse(null);
        return toView(r, activity, view(activity, instance, occupied), lastPayment);
    }

    /**
     * Si todavía se puede pagar (CU-15): está PENDIENTE, no pasaron las 24 horas desde que se creó
     * y no cerraron las reservas de esa fecha.
     */
    boolean payable(Reservation r, LocalDateTime now) {
        return r.getStatus() == ReservationStatus.PENDING
                && now.isBefore(r.getCreatedAt().plusHours(ReservationView.HOURS_TO_PAY))
                && !bookingClosed(r.getInstance().getActivity(), r.getInstance(), now);
    }

    // ---------- Verificaciones del paso 4 ----------

    private record Checked(Activity activity, ActivityInstance instance, ReservationQuote quote) {
    }

    private Checked check(Long activityId, ReserveRequest request) {
        Activity activity = publishedActivity(activityId);
        LocalDateTime now = LocalDateTime.now(clock);

        ActivityInstance instance = instanceRepository.findById(request.getInstanceId())
                .filter(i -> i.getActivity().getId().equals(activityId))
                .filter(i -> i.getStartAt().isAfter(now))
                .orElseThrow(DateNotAvailableException::new);

        // 4b. Se superó el límite de minutos previos al inicio
        if (bookingClosed(activity, instance, now)) {
            throw new BookingClosedException();
        }

        // 4a. No hay cupos suficientes (solo cuentan las reservas pagas)
        long occupied = occupiedSeats(List.of(instance.getId())).getOrDefault(instance.getId(), 0L);
        ActivityDateView date = view(activity, instance, occupied);
        if (!date.unlimited() && date.freeSeats() < request.getParticipants()) {
            throw new NotEnoughSeatsException(date.freeSeats());
        }

        List<CompanionForm> companions = request.getCompanions() == null ? List.of()
                : request.getCompanions().stream().map(c -> c == null ? new CompanionForm() : c.trimmed()).toList();
        return new Checked(activity, instance, new ReservationQuote(activity.getId(), activity.getName(),
                activity.getBusiness().getName(), location(activity), date, request.getParticipants(),
                activity.getPrice(), companions));
    }

    /** Las reservas cierran X minutos antes del inicio; con 0, a la hora de comienzo. */
    static boolean bookingClosed(Activity activity, ActivityInstance instance, LocalDateTime now) {
        return !now.isBefore(instance.getStartAt().minusMinutes(activity.getBookingCutoffMinutes()));
    }

    private Activity publishedActivity(Long activityId) {
        return activityRepository.findById(activityId)
                .filter(Activity::isActive)
                .orElseThrow(ActivityNotFoundException::new);
    }

    private Map<Long, Long> occupiedSeats(Collection<Long> instanceIds) {
        if (instanceIds.isEmpty()) return Map.of();
        return reservationRepository.occupiedSeats(instanceIds, ActivityDateService.SEAT_TAKING).stream()
                .collect(Collectors.toMap(ReservationRepository.OccupiedSeats::getInstanceId,
                        ReservationRepository.OccupiedSeats::getSeats));
    }

    private static ActivityDateView view(Activity activity, ActivityInstance instance, long occupied) {
        return new ActivityDateView(instance.getId(), instance.getStartAt(),
                instance.getStartAt().plusMinutes(activity.getDurationMinutes()), instance.getMaxCapacity(), occupied);
    }

    static String location(Activity activity) {
        String locality = activity.getLocality().getName();
        String department = activity.getLocality().getDepartment().getName();
        return locality.equalsIgnoreCase(department) ? department : locality + ", " + department;
    }

    private ReservationView toView(Reservation r, Activity activity, ActivityDateView date, PaymentStatus lastPayment) {
        List<CompanionView> companions = r.getCompanions().stream()
                .map(c -> new CompanionView(c.getPosition(), c.getFirstName(), c.getLastName(), c.getNationalId(),
                        c.getPhone()))
                .toList();
        Tourist tourist = r.getTourist();
        return new ReservationView(r.getId(), activity.getId(), activity.getName(), activity.getBusiness().getName(),
                location(activity), date, r.getParticipants(), r.getTotalPrice(), r.getStatus(), r.getCreatedAt(),
                tourist.getFirstName() + " " + tourist.getLastName(), companions, lastPayment,
                payable(r, LocalDateTime.now(clock)));
    }
}
