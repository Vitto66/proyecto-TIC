package uy.edu.um.proytic2026.exception;

import lombok.Getter;

/** CU-15: la reserva ya no se puede pagar. */
@Getter
public class ReservationNotPayableException extends RuntimeException {

    public enum Reason {
        /** Ya no está pendiente (confirmada, vencida, ...). */
        NOT_PENDING,
        /** Pasaron las 24 horas para pagarla (RF-08). */
        EXPIRED,
        /** 2b. Se superó el límite de minutos previos al inicio. */
        BOOKING_CLOSED
    }

    private final Reason reason;

    public ReservationNotPayableException(Reason reason) {
        super("La reserva no se puede pagar: " + reason);
        this.reason = reason;
    }
}
