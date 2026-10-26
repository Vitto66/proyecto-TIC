package uy.edu.um.proytic2026.payment;

import lombok.extern.slf4j.Slf4j;
import uy.edu.um.proytic2026.entity.PaymentStatus;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de pagos simulado: no se conecta a nada. Se usa en los tests y cuando no están las
 * credenciales de Mercado Pago, para poder probar la reserva completa en la compu.
 * <p>
 * Igual que el sandbox de Mercado Pago, el resultado depende del nombre del titular:
 * APRO aprueba, OTHE rechaza, FUND rechaza por fondos insuficientes, SECU por código de
 * seguridad, CONT queda en revisión y ERROR simula que el servicio no responde. Cualquier otro
 * nombre aprueba.
 */
@Slf4j
public class SimulatedPaymentGateway implements PaymentGateway {

    static final String IN_PROCESS_PREFIX = "sim-cont-";

    @Override
    public String name() {
        return "SIMULATED";
    }

    @Override
    public boolean usesCardForm() {
        return false;
    }

    @Override
    public String publicKey() {
        return "";
    }

    @Override
    public PaymentResult charge(ChargeRequest request) {
        String holder = request.cardholderName() == null ? "" : request.cardholderName().trim().toUpperCase(Locale.ROOT);
        String id = "sim-" + UUID.randomUUID();
        log.info("Pago simulado de {} para {} con titular {}", request.amount(), request.externalReference(), holder);
        return switch (holder) {
            case "OTHE" -> new PaymentResult(PaymentStatus.REJECTED, id, "cc_rejected_other_reason");
            case "FUND" -> new PaymentResult(PaymentStatus.REJECTED, id, "cc_rejected_insufficient_amount");
            case "SECU" -> new PaymentResult(PaymentStatus.REJECTED, id, "cc_rejected_bad_filled_security_code");
            case "EXPI" -> new PaymentResult(PaymentStatus.REJECTED, id, "cc_rejected_bad_filled_date");
            case "CALL" -> new PaymentResult(PaymentStatus.REJECTED, id, "cc_rejected_call_for_authorize");
            case "CONT" -> new PaymentResult(PaymentStatus.IN_PROCESS, IN_PROCESS_PREFIX + UUID.randomUUID(),
                    "pending_contingency");
            case "ERROR" -> PaymentResult.unavailable("simulated_unavailable");
            default -> new PaymentResult(PaymentStatus.APPROVED, id, "accredited");
        };
    }

    /** Un pago que quedó en revisión (CONT) se aprueba al consultarlo de nuevo. */
    @Override
    public PaymentResult status(String transactionId) {
        return new PaymentResult(PaymentStatus.APPROVED, transactionId, "accredited");
    }

    @Override
    public Optional<PaymentResult> findApproved(String externalReference) {
        return Optional.empty();
    }

    @Override
    public PaymentResult refund(String transactionId, String idempotencyKey) {
        return new PaymentResult(PaymentStatus.REFUNDED, transactionId, "refunded");
    }
}
