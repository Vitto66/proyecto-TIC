package uy.edu.um.proytic2026.exception;

/** CU-18 1a: esa reserva ya tiene su valoración (se permite una sola por reserva). */
public class AlreadyReviewedException extends RuntimeException {
    public AlreadyReviewedException() {
        super("Ya valoraste esta reserva");
    }
}
