package uy.edu.um.proytic2026.dto;

/**
 * Cómo terminó la baja de una actividad (CU-10).
 *
 * @param refunded       reservas a las que se les devolvió el dinero.
 * @param refundsPending reservas pagas cuya devolución no se pudo hacer todavía (el servicio de
 *                       pagos no respondió): se reintentan desde "Mis actividades".
 */
public record CancelResult(String activityName, int cancelledConfirmed, int cancelledPending, int refunded,
                           int refundsPending) {

    /** El mensaje para el prestador. */
    public String message() {
        StringBuilder text = new StringBuilder("\"" + activityName + "\" se dio de baja y ya no aparece en el portal.");
        int cancelled = cancelledConfirmed + cancelledPending;
        if (cancelled > 0) {
            text.append(" Se cancelaron ").append(CancelPreview.reservations(cancelled))
                    .append(" y se avisó a cada turista.");
        }
        if (refunded > 0) {
            text.append(" Se devolvió el 100% del pago de ").append(CancelPreview.reservations(refunded)).append('.');
        }
        if (refundsPending > 0) {
            text.append(" El servicio de pagos no respondió para ").append(CancelPreview.reservations(refundsPending))
                    .append(": reintentá la devolución en unos minutos.");
        }
        return text.toString();
    }
}
