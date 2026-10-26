package uy.edu.um.proytic2026.payment;

import uy.edu.um.proytic2026.entity.PaymentStatus;

/**
 * Lo que respondió el servicio de pagos.
 *
 * @param transactionId el identificador del pago en el servicio, o null si no respondió.
 * @param detail        el motivo que informa el servicio (por ejemplo cc_rejected_insufficient_amount).
 */
public record PaymentResult(PaymentStatus status, String transactionId, String detail) {

    public static PaymentResult unavailable(String detail) {
        return new PaymentResult(PaymentStatus.UNAVAILABLE, null, detail);
    }

    public boolean approved() {
        return status == PaymentStatus.APPROVED;
    }

    /** El motivo de un rechazo, para mostrarle al turista. */
    public String rejectionMessage() {
        if (detail == null) return "La tarjeta fue rechazada.";
        return switch (detail) {
            case "cc_rejected_insufficient_amount" -> "La tarjeta no tiene fondos suficientes.";
            case "cc_rejected_bad_filled_security_code" -> "El código de seguridad no es correcto.";
            case "cc_rejected_bad_filled_date" -> "La fecha de vencimiento no es correcta.";
            case "cc_rejected_bad_filled_other", "cc_rejected_bad_filled_card_number" -> "Revisá los datos de la tarjeta.";
            case "cc_rejected_call_for_authorize" -> "Tenés que autorizar el pago con el banco de tu tarjeta.";
            case "cc_rejected_card_disabled" -> "La tarjeta está deshabilitada. Llamá a tu banco para activarla.";
            case "cc_rejected_duplicated_payment" -> "Ya hiciste un pago por este monto. Si necesitás volver a pagar, usá otra tarjeta.";
            case "cc_rejected_high_risk", "cc_rejected_blacklist" -> "El pago fue rechazado. Probá con otra tarjeta.";
            case "invalid_request" -> "No se pudo procesar la tarjeta. Revisá los datos y volvé a intentar.";
            default -> "La tarjeta fue rechazada. Probá de nuevo o usá otra tarjeta.";
        };
    }
}
