package uy.edu.um.proytic2026.exception;

/**
 * La reserva no se puede cancelar: la actividad no lo permite, ya pasó el plazo
 * o la reserva no está pendiente ni confirmada.
 */
public class ReservationNotCancellableException extends RuntimeException {
    public ReservationNotCancellableException() {
        super("Esta reserva ya no se puede cancelar");
    }
}
