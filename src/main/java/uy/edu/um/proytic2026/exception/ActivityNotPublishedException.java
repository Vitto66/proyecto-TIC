package uy.edu.um.proytic2026.exception;

/** La actividad está dada de baja: no se le agregan fechas (CU-09) ni se modifica o se vuelve a dar de baja (CU-10). */
public class ActivityNotPublishedException extends RuntimeException {
    public ActivityNotPublishedException() {
        super("La actividad está dada de baja");
    }
}
