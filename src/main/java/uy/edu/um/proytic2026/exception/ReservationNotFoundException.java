package uy.edu.um.proytic2026.exception;

/** La reserva no existe o es de otro turista. Las dos se tratan igual (404). */
public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException() {
        super("No existe la reserva");
    }
}
