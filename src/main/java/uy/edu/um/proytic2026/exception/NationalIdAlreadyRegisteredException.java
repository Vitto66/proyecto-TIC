package uy.edu.um.proytic2026.exception;

public class NationalIdAlreadyRegisteredException extends RuntimeException {
    public NationalIdAlreadyRegisteredException(String nationalId) {
        super("La cédula " + nationalId + " ya está registrada");
    }
}
