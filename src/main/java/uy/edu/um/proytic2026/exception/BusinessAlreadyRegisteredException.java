package uy.edu.um.proytic2026.exception;

/** CU-07 1a: el prestador ya tiene su emprendimiento registrado (solo se permite uno). */
public class BusinessAlreadyRegisteredException extends RuntimeException {
    public BusinessAlreadyRegisteredException() {
        super("Tu emprendimiento ya está registrado");
    }
}
