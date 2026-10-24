package uy.edu.um.proytic2026.exception;

/** Precondición del CU-09: solo se agregan fechas a una actividad publicada (no dada de baja). */
public class ActivityNotPublishedException extends RuntimeException {
    public ActivityNotPublishedException() {
        super("La actividad está dada de baja: no se le pueden agregar fechas");
    }
}
