package uy.edu.um.proytic2026.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String email) {
        super("El correo " + email + " ya está registrado como turista");
    }
}
