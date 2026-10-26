package uy.edu.um.proytic2026.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.proytic2026.dto.PayRequest;
import uy.edu.um.proytic2026.dto.ReservationView;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.PaymentStatus;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationPayment;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.exception.NotEnoughSeatsException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.exception.ReservationNotPayableException;
import uy.edu.um.proytic2026.exception.ReservationNotPayableException.Reason;
import uy.edu.um.proytic2026.payment.ChargeRequest;
import uy.edu.um.proytic2026.payment.PaymentGateway;
import uy.edu.um.proytic2026.payment.PaymentResult;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ReservationPaymentRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CU-15 Pagar una reserva, con el servicio de pagos externo ({@link PaymentGateway}).
 * <p>
 * Reglas (RF-08): nunca se confirma una reserva sin un pago aprobado, y nunca se cobra si ya no
 * hay lugar. Por eso:
 * <ol>
 *   <li>Antes de cobrar se verifica que siga pendiente, que no hayan pasado las 24 horas, que no
 *       hayan cerrado las reservas (2b) y que haya cupos (2a).</li>
 *   <li>El cobro se pide <b>fuera</b> de una transacción de la base, para no tener una conexión
 *       ocupada mientras se espera al servicio.</li>
 *   <li>Si se aprueba, se confirma bloqueando la fecha, así dos pagos simultáneos no ocupan el
 *       mismo lugar. Si justo se llenó, se devuelve el dinero.</li>
 *   <li>Si el servicio rechaza o no responde, la reserva sigue PENDIENTE y se puede reintentar.
 *       Antes de reintentar se busca si un intento anterior sin respuesta llegó a cobrarse, para
 *       no cobrar dos veces.</li>
 * </ol>
 * Cada intento se guarda en reservation_payment.
 */
@Slf4j
@Service
public class PaymentService {

    /** Cómo terminó un intento de pago, para mostrarle al turista. */
    public enum Outcome {
        CONFIRMED, ALREADY_CONFIRMED, REJECTED, IN_PROCESS, UNAVAILABLE, REFUNDED_NO_SEATS
    }

    public record PayResult(Outcome outcome, String message) {
    }

    /** Cómo terminó la devolución del dinero de una reserva cancelada (CU-10). */
    public enum RefundOutcome {
        /** No había nada cobrado. */
        NOTHING_TO_REFUND,
        /** Se devolvió el 100%. */
        REFUNDED,
        /** El servicio de pagos no respondió: queda pendiente para reintentar. */
        PENDING
    }

    /** Lo necesario de un pago guardado, leído dentro de una transacción. */
    private record StoredPayment(Long id, PaymentStatus status, String transactionId, BigDecimal amount) {
    }

    /** Lo que necesita la página de pago. */
    public record PayPage(ReservationView reservation, boolean cardForm, String publicKey, String payerEmail) {
    }

    private final ReservationRepository reservationRepository;
    private final ReservationPaymentRepository paymentRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final ReservationService reservationService;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String payerEmailOverride;

    public PaymentService(ReservationRepository reservationRepository, ReservationPaymentRepository paymentRepository,
                          ActivityInstanceRepository instanceRepository, ReservationService reservationService,
                          PaymentGateway gateway, PlatformTransactionManager transactionManager, Clock clock,
                          @Value("${app.payments.mercadopago.payer-email:}") String payerEmailOverride) {
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.instanceRepository = instanceRepository;
        this.reservationService = reservationService;
        this.gateway = gateway;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.payerEmailOverride = payerEmailOverride == null ? "" : payerEmailOverride.trim();
    }

    /** Datos de lo que se va a cobrar, tomados de la base dentro de una transacción. */
    private record Charge(Long reservationId, BigDecimal amount, String description, String payerEmail,
                          String nationalId, boolean alreadyConfirmed, boolean inProcess) {
    }

    /** La página de pago: la reserva y qué formulario de tarjeta mostrar. */
    public PayPage page(Long touristId, Long reservationId) {
        ReservationView view = reservationService.reservation(touristId, reservationId);
        String email = tx.execute(s -> payerEmail(owned(touristId, reservationId)));
        return new PayPage(view, gateway.usesCardForm(), gateway.publicKey(), email);
    }

    /**
     * Pasos 1 a 6: verifica, cobra con el servicio externo y, si se aprueba, confirma la reserva.
     *
     * @throws ReservationNotFoundException   no existe o es de otro turista.
     * @throws ReservationNotPayableException ya no está pendiente, venció o cerraron las reservas (2b).
     * @throws NotEnoughSeatsException        2a. Otro turista ocupó los lugares mientras tanto: no se cobra.
     */
    public PayResult pay(Long touristId, Long reservationId, PayRequest request) {
        Charge charge = tx.execute(s -> prepare(touristId, reservationId));
        if (charge.alreadyConfirmed()) {
            return new PayResult(Outcome.ALREADY_CONFIRMED, "Esta reserva ya está paga.");
        }
        if (charge.inProcess()) {
            return new PayResult(Outcome.IN_PROCESS,
                    "Tu pago anterior todavía se está procesando. Consultá su estado antes de volver a pagar.");
        }

        String reference = "reserva-" + reservationId;
        // ¿Un intento anterior que se cortó llegó a cobrarse? Entonces no se cobra de nuevo
        Optional<PaymentResult> previous = gateway.findApproved(reference);
        PaymentResult result;
        if (previous.isPresent()) {
            result = previous.get();
            if (!result.approved()) {
                record(reservationId, charge.amount(), result);
                return unavailable();
            }
            log.info("La reserva {} ya tenía un pago aprobado ({}): no se cobra de nuevo", reservationId,
                    result.transactionId());
        } else {
            String document = blankToNull(request.getDocumentNumber());
            result = gateway.charge(new ChargeRequest(UUID.randomUUID().toString(), charge.amount(),
                    charge.description(), reference, request.getToken(), request.getPaymentMethodId(),
                    request.getIssuerId(), request.getInstallments() == null ? 1 : request.getInstallments(),
                    charge.payerEmail(), document == null ? "CI" : request.getDocumentType(),
                    document == null ? charge.nationalId() : document, request.getCardholderName()));
        }
        return settle(reservationId, charge.amount(), result);
    }

    /** Consulta un pago que quedó en revisión (por ejemplo, CONT en el sandbox). */
    public PayResult refresh(Long touristId, Long reservationId) {
        ReservationPayment last = tx.execute(s -> {
            Reservation r = owned(touristId, reservationId);
            return paymentRepository.findFirstByReservationIdOrderByIdDesc(r.getId())
                    .filter(p -> p.getStatus() == PaymentStatus.IN_PROCESS && p.getTransactionId() != null)
                    .orElse(null);
        });
        if (last == null) {
            return new PayResult(Outcome.REJECTED, "No hay ningún pago en revisión para esta reserva.");
        }
        PaymentResult result = gateway.status(last.getTransactionId());
        if (result.status() == PaymentStatus.UNAVAILABLE) {
            return unavailable();
        }
        if (result.status() == PaymentStatus.IN_PROCESS) {
            return new PayResult(Outcome.IN_PROCESS, "El pago sigue en revisión. Probá de nuevo en unos minutos.");
        }
        tx.executeWithoutResult(s -> paymentRepository.findById(last.getId()).ifPresent(p -> {
            p.setStatus(result.status());
            p.setStatusDetail(result.detail());
        }));
        return result.approved() ? confirmOrRefund(reservationId, last.getId(), result)
                : new PayResult(Outcome.REJECTED, result.rejectionMessage());
    }

    /**
     * CU-10 2a: devuelve el 100% de lo cobrado de una reserva (que ya se canceló). Se puede llamar
     * más de una vez: lo ya devuelto no se vuelve a devolver. Un pago que estaba en revisión se
     * consulta primero, y si un intento quedó sin respuesta se busca si llegó a cobrarse.
     */
    public RefundOutcome refundAll(Long reservationId) {
        List<StoredPayment> payments = tx.execute(s -> paymentRepository.findByReservationIdOrderByIdAsc(reservationId)
                .stream().map(p -> new StoredPayment(p.getId(), p.getStatus(), p.getTransactionId(), p.getAmount()))
                .toList());
        boolean pending = false;
        boolean refunded = false;
        boolean anyCharged = false;

        for (StoredPayment p : payments) {
            PaymentStatus status = p.status();
            if (status == PaymentStatus.IN_PROCESS && p.transactionId() != null) {
                PaymentResult current = gateway.status(p.transactionId());
                if (current.status() == PaymentStatus.UNAVAILABLE || current.status() == PaymentStatus.IN_PROCESS) {
                    pending = true;
                    continue;
                }
                status = current.status();
                updateStatus(p.id(), status, current.detail());
            }
            if (status == PaymentStatus.APPROVED && p.transactionId() != null) {
                anyCharged = true;
                if (refundOne(p.id(), p.transactionId())) refunded = true;
                else pending = true;
            }
            if (status == PaymentStatus.REFUNDED) anyCharged = true;
        }

        // Un intento sin respuesta puede haberse cobrado igual
        boolean hadNoAnswer = payments.stream().anyMatch(p -> p.status() == PaymentStatus.UNAVAILABLE);
        if (hadNoAnswer && !anyCharged) {
            Optional<PaymentResult> found = gateway.findApproved("reserva-" + reservationId);
            if (found.isPresent() && !found.get().approved()) {
                pending = true;
            } else if (found.isPresent()) {
                BigDecimal amount = payments.get(payments.size() - 1).amount();
                Long id = record(reservationId, amount, found.get());
                if (refundOne(id, found.get().transactionId())) refunded = true;
                else pending = true;
            }
        }
        return pending ? RefundOutcome.PENDING : refunded ? RefundOutcome.REFUNDED : RefundOutcome.NOTHING_TO_REFUND;
    }

    private boolean refundOne(Long paymentId, String transactionId) {
        PaymentResult result = gateway.refund(transactionId, "refund-" + paymentId);
        if (result.status() != PaymentStatus.REFUNDED) {
            log.warn("No se pudo devolver el pago {}: queda pendiente para reintentar", transactionId);
            return false;
        }
        updateStatus(paymentId, PaymentStatus.REFUNDED, "activity_cancelled");
        return true;
    }

    private void updateStatus(Long paymentId, PaymentStatus status, String detail) {
        tx.executeWithoutResult(s -> paymentRepository.findById(paymentId).ifPresent(p -> {
            p.setStatus(status);
            p.setStatusDetail(truncate(detail));
        }));
    }

    // ---------- Pasos ----------

    private Charge prepare(Long touristId, Long reservationId) {
        Reservation r = owned(touristId, reservationId);
        if (r.getStatus() == ReservationStatus.CONFIRMED) {
            return new Charge(r.getId(), r.getTotalPrice(), null, null, null, true, false);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (r.getStatus() != ReservationStatus.PENDING) {
            throw new ReservationNotPayableException(Reason.NOT_PENDING);
        }
        if (!now.isBefore(r.getCreatedAt().plusHours(ReservationView.HOURS_TO_PAY))) {
            throw new ReservationNotPayableException(Reason.EXPIRED);
        }
        ActivityInstance instance = r.getInstance();
        if (ReservationService.bookingClosed(instance.getActivity(), instance, now)) {
            throw new ReservationNotPayableException(Reason.BOOKING_CLOSED);
        }
        long free = freeSeats(instance);
        if (free < r.getParticipants()) {
            throw new NotEnoughSeatsException(Math.max(0, free));
        }
        boolean inProcess = paymentRepository.findFirstByReservationIdOrderByIdDesc(r.getId())
                .map(p -> p.getStatus() == PaymentStatus.IN_PROCESS).orElse(false);
        String description = "Reserva #" + r.getId() + " · " + instance.getActivity().getName();
        return new Charge(r.getId(), r.getTotalPrice(), description, payerEmail(r),
                r.getTourist().getNationalId(), false, inProcess);
    }

    /** Guarda el intento y, si se aprobó, confirma la reserva. */
    private PayResult settle(Long reservationId, BigDecimal amount, PaymentResult result) {
        Long paymentId = record(reservationId, amount, result);
        return switch (result.status()) {
            case APPROVED -> confirmOrRefund(reservationId, paymentId, result);
            case IN_PROCESS -> new PayResult(Outcome.IN_PROCESS, "El servicio de pagos está revisando tu pago. "
                    + "Tu reserva se confirma cuando lo apruebe: consultá el estado en unos minutos.");
            case UNAVAILABLE -> unavailable();
            default -> new PayResult(Outcome.REJECTED, result.rejectionMessage());
        };
    }

    /**
     * Paso 5 y 6: con el pago aprobado, se confirma bloqueando la fecha y verificando los cupos otra
     * vez. Si mientras tanto se llenó, se devuelve el dinero (nunca se queda cobrado sin lugar).
     */
    private PayResult confirmOrRefund(Long reservationId, Long paymentId, PaymentResult result) {
        Boolean confirmed = tx.execute(s -> {
            Reservation r = reservationRepository.findById(reservationId).orElseThrow(ReservationNotFoundException::new);
            if (r.getStatus() == ReservationStatus.CONFIRMED) return true;
            ActivityInstance instance = instanceRepository.findLockedById(r.getInstance().getId())
                    .orElseThrow(ReservationNotFoundException::new);
            if (r.getStatus() != ReservationStatus.PENDING || freeSeats(instance) < r.getParticipants()) {
                return false;
            }
            r.setStatus(ReservationStatus.CONFIRMED);
            return true;
        });
        if (Boolean.TRUE.equals(confirmed)) {
            log.info("Reserva {} confirmada con el pago {}", reservationId, result.transactionId());
            return new PayResult(Outcome.CONFIRMED, "¡Pago aprobado! Tu reserva está confirmada.");
        }
        PaymentResult refund = gateway.refund(result.transactionId(), "refund-" + paymentId);
        if (refund.status() == PaymentStatus.REFUNDED) {
            tx.executeWithoutResult(s -> paymentRepository.findById(paymentId).ifPresent(p -> {
                p.setStatus(PaymentStatus.REFUNDED);
                p.setStatusDetail("no_seats");
            }));
        } else {
            log.error("Hay que devolver a mano el pago {} de la reserva {}: no quedaban lugares",
                    result.transactionId(), reservationId);
        }
        return new PayResult(Outcome.REFUNDED_NO_SEATS, "Mientras pagabas, otra persona ocupó los últimos lugares. "
                + "Se te devolvió el dinero. Elegí otra fecha.");
    }

    private Long record(Long reservationId, BigDecimal amount, PaymentResult result) {
        return tx.execute(s -> {
            // Si el pago ya estaba guardado (lo encontró la búsqueda), no se duplica
            if (result.transactionId() != null) {
                Optional<ReservationPayment> existing = paymentRepository.findByReservationIdOrderByIdAsc(reservationId)
                        .stream().filter(p -> result.transactionId().equals(p.getTransactionId())).findFirst();
                if (existing.isPresent()) {
                    existing.get().setStatus(result.status());
                    return existing.get().getId();
                }
            }
            Reservation r = reservationRepository.getReferenceById(reservationId);
            return paymentRepository.save(ReservationPayment.builder().reservation(r).provider(gateway.name())
                    .transactionId(result.transactionId()).status(result.status())
                    .statusDetail(truncate(result.detail())).amount(amount)
                    .createdAt(LocalDateTime.now(clock)).build()).getId();
        });
    }

    private static PayResult unavailable() {
        return new PayResult(Outcome.UNAVAILABLE, "El servicio de pagos no está respondiendo y no se cobró nada. "
                + "Tu reserva sigue pendiente: probá de nuevo en unos minutos.");
    }

    private Reservation owned(Long touristId, Long reservationId) {
        return reservationRepository.findById(reservationId)
                .filter(r -> r.getTourist().getId().equals(touristId))
                .orElseThrow(ReservationNotFoundException::new);
    }

    private long freeSeats(ActivityInstance instance) {
        if (instance.getMaxCapacity() == null) return Long.MAX_VALUE;
        long occupied = reservationRepository.occupiedSeats(List.of(instance.getId()), ActivityDateService.SEAT_TAKING)
                .stream().mapToLong(ReservationRepository.OccupiedSeats::getSeats).sum();
        return instance.getMaxCapacity() - occupied;
    }

    private String payerEmail(Reservation r) {
        return payerEmailOverride.isEmpty() ? r.getTourist().getUser().getEmail() : payerEmailOverride;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String detail) {
        return detail == null || detail.length() <= 100 ? detail : detail.substring(0, 100);
    }
}
