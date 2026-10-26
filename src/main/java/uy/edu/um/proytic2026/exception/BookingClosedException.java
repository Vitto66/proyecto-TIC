package uy.edu.um.proytic2026.exception;

/** CU-14 4b: se superó el límite de minutos previos al inicio; ya no se admiten reservas. */
public class BookingClosedException extends RuntimeException {
    public BookingClosedException() {
        super("Ya no se admiten reservas para esa fecha");
    }
}
