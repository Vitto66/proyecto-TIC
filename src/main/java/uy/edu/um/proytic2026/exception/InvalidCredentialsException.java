package uy.edu.um.proytic2026.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Credenciales incorrectas");
    }
}
