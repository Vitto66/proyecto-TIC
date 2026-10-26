package uy.edu.um.proytic2026.exception;

/** La fecha no existe o es de una actividad de otro prestador. */
public class InstanceNotFoundException extends RuntimeException {
    public InstanceNotFoundException() {
        super("No existe esa fecha");
    }
}
