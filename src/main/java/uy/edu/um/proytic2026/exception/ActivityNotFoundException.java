package uy.edu.um.proytic2026.exception;

/**
 * La actividad no existe o no es del prestador que la pide. Las dos se tratan igual (404), para
 * no revelar qué actividades existen de otros prestadores.
 */
public class ActivityNotFoundException extends RuntimeException {
    public ActivityNotFoundException() {
        super("No existe la actividad");
    }
}
