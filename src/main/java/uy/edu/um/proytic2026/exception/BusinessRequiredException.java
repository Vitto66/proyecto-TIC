package uy.edu.um.proytic2026.exception;

/** Precondición del CU-08: para publicar actividades hay que tener el emprendimiento registrado (CU-07). */
public class BusinessRequiredException extends RuntimeException {
    public BusinessRequiredException() {
        super("Antes de publicar actividades tenés que registrar tu emprendimiento");
    }
}
