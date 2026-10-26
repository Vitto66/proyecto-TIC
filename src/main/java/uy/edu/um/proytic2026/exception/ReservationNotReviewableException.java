package uy.edu.um.proytic2026.exception;

/** CU-18 precondición: solo se valoran las reservas FINALIZADAS (la actividad ya pasó). */
public class ReservationNotReviewableException extends RuntimeException {
    public ReservationNotReviewableException() {
        super("La reserva todavía no está finalizada");
    }
}
