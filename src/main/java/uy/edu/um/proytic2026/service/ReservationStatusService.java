package uy.edu.um.proytic2026.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.proytic2026.dto.DateLabels;
import uy.edu.um.proytic2026.dto.ReservationView;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.entity.TouristNotification;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.TouristNotificationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-16 Actualizar el estado de las reservas. Lo corre periódicamente {@link ReservationStatusJob}.
 * <ul>
 *   <li>Pasos 2 a 4: las PENDIENTES que superaron las 24 horas sin pago aprobado, o cuya fecha ya
 *       cerró las reservas por el límite de minutos previos, pasan a VENCIDA y se avisa al turista.</li>
 *   <li>Paso 5: las CONFIRMADAS cuya fecha ya terminó pasan a FINALIZADA (se pueden valorar, CU-18).</li>
 * </ul>
 * Cada reserva pendiente se revisa en su propia transacción y con la fecha bloqueada, igual que al
 * confirmar un pago (CU-15): si el turista paga justo en ese momento, una de las dos operaciones
 * espera a la otra y ve el estado nuevo (2a: si ya quedó confirmada, no se vence).
 */
@Slf4j
@Service
public class ReservationStatusService {

    /** Cuántas reservas cambió una pasada. */
    public record UpdateResult(int expired, int finished) {
    }

    private final ReservationRepository reservationRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final TouristNotificationRepository notificationRepository;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ReservationStatusService(ReservationRepository reservationRepository,
                                    ActivityInstanceRepository instanceRepository,
                                    TouristNotificationRepository notificationRepository,
                                    PlatformTransactionManager transactionManager, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.instanceRepository = instanceRepository;
        this.notificationRepository = notificationRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Una pasada completa (paso 1). */
    public UpdateResult update() {
        LocalDateTime now = LocalDateTime.now(clock);
        int expired = 0;
        for (Long id : reservationRepository.findIdsByStatus(ReservationStatus.PENDING)) {
            try {
                if (Boolean.TRUE.equals(tx.execute(s -> expireIfDue(id, now)))) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                // Una reserva con problemas no frena a las demás: se reintenta en la próxima pasada
                log.error("No se pudo revisar la reserva pendiente {}", id, ex);
            }
        }
        Integer finished = tx.execute(s -> finishEnded(now));
        UpdateResult result = new UpdateResult(expired, finished == null ? 0 : finished);
        if (result.expired() > 0 || result.finished() > 0) {
            log.info("Reservas actualizadas: {} vencidas, {} finalizadas", result.expired(), result.finished());
        }
        return result;
    }

    /** Por qué vence una reserva pendiente. */
    enum ExpiryReason { NOT_PAID_IN_TIME, BOOKING_CLOSED }

    /** Por qué vence una reserva pendiente, o null si todavía se puede pagar (paso 2). */
    static ExpiryReason expiryReason(Reservation r, LocalDateTime now) {
        if (!now.isBefore(r.getCreatedAt().plusHours(ReservationView.HOURS_TO_PAY))) {
            return ExpiryReason.NOT_PAID_IN_TIME;
        }
        ActivityInstance instance = r.getInstance();
        if (ReservationService.bookingClosed(instance.getActivity(), instance, now)) {
            return ExpiryReason.BOOKING_CLOSED;
        }
        return null;
    }

    /** Cuándo termina una fecha: el inicio más la duración de la actividad. */
    static LocalDateTime endOf(ActivityInstance instance) {
        return instance.getStartAt().plusMinutes(instance.getActivity().getDurationMinutes());
    }

    // ---------- Pasos 2 a 4 ----------

    private boolean expireIfDue(Long reservationId, LocalDateTime now) {
        Long instanceId = reservationRepository.findInstanceIdById(reservationId).orElse(null);
        if (instanceId == null) {
            return false;
        }
        // Primero se bloquea la fecha (como el pago) y recién después se mira el estado actual
        instanceRepository.findLockedById(instanceId);
        if (reservationRepository.findStatusById(reservationId).orElse(null) != ReservationStatus.PENDING) {
            return false; // 2a: el turista pagó mientras tanto
        }
        Reservation r = reservationRepository.findById(reservationId).orElseThrow();
        ExpiryReason reason = expiryReason(r, now);
        if (reason == null) {
            return false;
        }
        r.setStatus(ReservationStatus.EXPIRED);
        notificationRepository.save(TouristNotification.builder()
                .tourist(r.getTourist())
                .reservation(r)
                .message(expiredMessage(r, reason))
                .createdAt(now)
                .build());
        return true;
    }

    static String expiredMessage(Reservation r, ExpiryReason reason) {
        ActivityInstance instance = r.getInstance();
        Activity activity = instance.getActivity();
        String when = DateLabels.day(instance.getStartAt().toLocalDate()).toLowerCase()
                + " a las " + DateLabels.time(instance.getStartAt());
        String why = reason == ExpiryReason.NOT_PAID_IN_TIME
                ? "pasaron 24 horas sin que se aprobara el pago"
                : "se cerraron las reservas para esa fecha antes de que se aprobara el pago";
        return "Tu reserva de «" + activity.getName() + "» para el " + when + " venció porque " + why
                + ". No se te cobró nada.";
    }

    // ---------- Paso 5 ----------

    private int finishEnded(LocalDateTime now) {
        List<Reservation> started = reservationRepository.findConfirmedStartedBy(now);
        int finished = 0;
        for (Reservation r : started) {
            if (!now.isBefore(endOf(r.getInstance()))) {
                r.setStatus(ReservationStatus.FINISHED);
                finished++;
            }
        }
        return finished;
    }
}
