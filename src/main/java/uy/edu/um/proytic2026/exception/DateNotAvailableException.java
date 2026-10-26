package uy.edu.um.proytic2026.exception;

/**
 * La fecha elegida no es de esa actividad o ya pasó. Puede pasar si se manipula el formulario,
 * porque el id llega desde el navegador.
 */
public class DateNotAvailableException extends RuntimeException {
    public DateNotAvailableException() {
        super("Esa fecha no está disponible para esta actividad");
    }
}
