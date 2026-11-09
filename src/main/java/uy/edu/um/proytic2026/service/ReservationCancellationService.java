package uy.edu.um.proytic2026.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.exception.ReservationNotCancellableException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.service.PaymentService.RefundOutcome;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * El turista cancela su reserva, si la actividad lo permite y todavía está en plazo.
 * <ul>
 *   <li>El prestador decide al publicar la actividad si se puede cancelar y hasta cuántas
 *       horas antes del inicio.</li>
 *   <li>La reserva pasa a CANCELADA y se devuelve el 100% de lo pagado (el "pago negativo").
 *       Si no estaba paga, no hay nada que devolver.</li>
 *   <li>Como en CU-10, la devolución se pide fuera de la transacción: si el servicio de pagos no
 *       responde, la reserva igual queda cancelada y la devolución se puede reintentar.</li>
 * </ul>
 */
@Slf4j
@Service
public class ReservationCancellationService {

    private final ReservationRepository reservationRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final PaymentService paymentService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ReservationCancellationService(ReservationRepository reservationRepository,
                                          ActivityInstanceRepository instanceRepository, PaymentService paymentService,
                                          PlatformTransactionManager transactionManager, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.instanceRepository = instanceRepository;
        this.paymentService = paymentService;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Cancela la reserva del turista y le devuelve lo que pagó.
     * Si ya la había cancelado él y la devolución quedó pendiente, solo la reintenta.
     *
     * @throws ReservationNotFoundException       no existe o es de otro turista.
     * @throws ReservationNotCancellableException la actividad no lo permite, pasó el plazo o ya no está activa.
     */
    public RefundOutcome cancel(Long touristId, Long reservationId) {
        tx.executeWithoutResult(s -> {
            Long instanceId = reservationRepository.findInstanceIdById(reservationId)
                    .orElseThrow(ReservationNotFoundException::new);
            // Se bloquea la fecha, igual que al confirmar un pago: así un pago que llega a la vez
            // ve la reserva ya cancelada y devuelve el dinero en vez de confirmarla
            instanceRepository.findLockedById(instanceId).orElseThrow(ReservationNotFoundException::new);
            Reservation r = reservationRepository.findById(reservationId)
                    .filter(found -> found.getTourist().getId().equals(touristId))
                    .orElseThrow(ReservationNotFoundException::new);
            if (r.getStatus() == ReservationStatus.CANCELLED && r.isCancelledByTourist()) {
                return; // ya la canceló: solo falta (re)intentar la devolución
            }
            if (!cancellable(r, LocalDateTime.now(clock))) {
                throw new ReservationNotCancellableException();
            }
            r.setStatus(ReservationStatus.CANCELLED);
            r.setCancelledByTourist(true);
        });

        RefundOutcome outcome = paymentService.refundAll(reservationId);
        log.info("Reserva {} cancelada por el turista: devolución {}", reservationId, outcome);
        return outcome;
    }

    /**
     * Si el turista todavía puede cancelarla: está pendiente o confirmada, la actividad permite
     * cancelar y faltan más de las horas que fijó el prestador para el inicio.
     */
    public static boolean cancellable(Reservation r, LocalDateTime now) {
        if (r.getStatus() != ReservationStatus.PENDING && r.getStatus() != ReservationStatus.CONFIRMED) {
            return false;
        }
        var activity = r.getInstance().getActivity();
        return activity.isCancellationAllowed() && now.isBefore(cancelDeadline(r));
    }

    /** El momento hasta el que se puede cancelar. */
    public static LocalDateTime cancelDeadline(Reservation r) {
        return r.getInstance().getStartAt().minusHours(r.getInstance().getActivity().getCancellationHours());
    }
}
