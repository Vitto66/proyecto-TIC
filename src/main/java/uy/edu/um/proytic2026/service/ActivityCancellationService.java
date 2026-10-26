package uy.edu.um.proytic2026.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.proytic2026.dto.CancelPreview;
import uy.edu.um.proytic2026.dto.CancelResult;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.PaymentStatus;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.ActivityNotPublishedException;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ReservationPaymentRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.service.PaymentService.RefundOutcome;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-10 Dar de baja una actividad (la parte de modificarla está en {@link ActivityService}).
 * <ul>
 *   <li>La actividad queda con active = false: sale del portal y no se puede reservar.</li>
 *   <li>2a. Sus reservas CONFIRMADAS en fechas futuras pasan a CANCELADA y se le pide al servicio
 *       de pagos el reembolso del 100%.</li>
 *   <li>2b. Sus reservas PENDIENTES de pago pasan a CANCELADA.</li>
 *   <li>Se notifica a cada turista.</li>
 * </ul>
 * Los reembolsos se piden fuera de la transacción de la base. Si el servicio de pagos no
 * responde, la reserva igual queda cancelada y la devolución queda pendiente para reintentar.
 */
@Slf4j
@Service
public class ActivityCancellationService {

    private static final Set<ReservationStatus> CANCELLABLE = Set.of(ReservationStatus.CONFIRMED, ReservationStatus.PENDING);
    private static final Set<PaymentStatus> PAID = Set.of(PaymentStatus.APPROVED, PaymentStatus.IN_PROCESS);

    private final ActivityRepository activityRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationPaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final TouristNotifier notifier;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ActivityCancellationService(ActivityRepository activityRepository, ReservationRepository reservationRepository,
                                       ReservationPaymentRepository paymentRepository, PaymentService paymentService,
                                       TouristNotifier notifier, PlatformTransactionManager transactionManager,
                                       Clock clock) {
        this.activityRepository = activityRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.notifier = notifier;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Una reserva cancelada, con lo necesario para devolver y avisar fuera de la transacción. */
    private record Cancelled(Long reservationId, boolean wasConfirmed, String email, String touristName) {
    }

    private record Cancellation(String activityName, List<Cancelled> reservations) {
    }

    /**
     * Paso 2: cuántas reservas se verían afectadas, para pedir confirmación (2a).
     *
     * @throws ActivityNotFoundException     no existe o no es del prestador.
     * @throws ActivityNotPublishedException ya estaba dada de baja.
     */
    public CancelPreview preview(Long providerId, Long activityId) {
        return tx.execute(s -> {
            Activity activity = ownedActive(providerId, activityId);
            List<Reservation> upcoming = reservationRepository.findUpcomingByActivity(activityId,
                    LocalDateTime.now(clock), CANCELLABLE);
            List<Reservation> confirmed = upcoming.stream()
                    .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED).toList();
            return new CancelPreview(activity.getId(), activity.getName(), confirmed.size(),
                    confirmed.stream().mapToLong(Reservation::getParticipants).sum(),
                    upcoming.size() - confirmed.size());
        });
    }

    /** Pasos 3 y 4 (y 2a, 2b): da de baja la actividad, cancela sus reservas y devuelve los pagos. */
    public CancelResult cancel(Long providerId, Long activityId) {
        Cancellation cancellation = tx.execute(s -> {
            Activity activity = ownedActive(providerId, activityId);
            activity.setActive(false);
            List<Cancelled> cancelled = new ArrayList<>();
            for (Reservation r : reservationRepository.findUpcomingByActivity(activityId, LocalDateTime.now(clock),
                    CANCELLABLE)) {
                cancelled.add(new Cancelled(r.getId(), r.getStatus() == ReservationStatus.CONFIRMED,
                        r.getTourist().getUser().getEmail(),
                        r.getTourist().getFirstName() + " " + r.getTourist().getLastName()));
                r.setStatus(ReservationStatus.CANCELLED);
            }
            return new Cancellation(activity.getName(), cancelled);
        });

        int refunded = 0;
        int pending = 0;
        for (Cancelled c : cancellation.reservations()) {
            // También las pendientes: un pago que quedó en revisión o sin respuesta pudo haberse cobrado
            RefundOutcome outcome = paymentService.refundAll(c.reservationId());
            if (outcome == RefundOutcome.REFUNDED) refunded++;
            if (outcome == RefundOutcome.PENDING) pending++;
            notifier.reservationCancelled(c.email(), c.touristName(), cancellation.activityName(), c.reservationId(),
                    outcome);
        }
        int confirmed = (int) cancellation.reservations().stream().filter(Cancelled::wasConfirmed).count();
        log.info("Actividad {} dada de baja: {} reservas canceladas, {} reembolsadas, {} reembolsos pendientes",
                activityId, cancellation.reservations().size(), refunded, pending);
        return new CancelResult(cancellation.activityName(), confirmed, cancellation.reservations().size() - confirmed,
                refunded, pending);
    }

    /** Reintenta las devoluciones que quedaron pendientes porque el servicio de pagos no respondió. */
    public CancelResult retryRefunds(Long providerId, Long activityId) {
        String name = tx.execute(s -> owned(providerId, activityId).getName());
        List<Long> reservationIds = paymentRepository.reservationsWithPendingRefund(activityId,
                ReservationStatus.CANCELLED, PAID);
        int refunded = 0;
        int pending = 0;
        for (Long id : reservationIds) {
            RefundOutcome outcome = paymentService.refundAll(id);
            if (outcome == RefundOutcome.REFUNDED) refunded++;
            if (outcome == RefundOutcome.PENDING) pending++;
        }
        return new CancelResult(name, 0, 0, refunded, pending);
    }

    /** "Mis actividades": cuántas devoluciones tiene pendientes cada actividad del prestador. */
    public Map<Long, Long> pendingRefunds(Long providerId) {
        return paymentRepository.countPendingRefundsByActivity(providerId, ReservationStatus.CANCELLED, PAID).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private Activity ownedActive(Long providerId, Long activityId) {
        Activity activity = owned(providerId, activityId);
        if (!activity.isActive()) {
            throw new ActivityNotPublishedException();
        }
        return activity;
    }

    private Activity owned(Long providerId, Long activityId) {
        return activityRepository.findById(activityId)
                .filter(a -> a.getBusiness().getProvider().getId().equals(providerId))
                .orElseThrow(ActivityNotFoundException::new);
    }
}
