package uy.edu.um.proytic2026.exception;

/** Precondición del CU-07: solo un prestador APROBADO puede registrar su emprendimiento. */
public class ProviderNotApprovedException extends RuntimeException {
    public ProviderNotApprovedException() {
        super("Tu cuenta de prestador todavía no fue aprobada por un administrador");
    }
}
