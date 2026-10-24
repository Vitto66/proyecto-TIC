package uy.edu.um.proytic2026.exception;

/** CU-09 3a: la fecha u hora elegida ya pasó. */
public class PastDateException extends RuntimeException {
    public PastDateException() {
        super("La fecha y hora tienen que ser futuras");
    }
}
