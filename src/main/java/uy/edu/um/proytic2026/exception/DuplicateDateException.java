package uy.edu.um.proytic2026.exception;

/**
 * Ya hay una instancia de la misma actividad que empieza exactamente a esa hora. La base no lo
 * permite (UNIQUE (activity_id, start_at)), así que no se puede confirmar como en 3b.
 */
public class DuplicateDateException extends RuntimeException {
    public DuplicateDateException() {
        super("Ya hay una fecha de esta actividad que empieza a esa hora");
    }
}
