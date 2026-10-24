package uy.edu.um.proytic2026.exception;

import lombok.Getter;

/**
 * Se eligió una opción que no existe o ya no está disponible (por ejemplo, un tipo de actividad
 * que el administrador desactivó). Los valores llegan desde el navegador, por eso se revisan.
 */
@Getter
public class InvalidSelectionException extends RuntimeException {

    /** Campo del formulario al que corresponde el error. */
    private final String field;

    public InvalidSelectionException(String field, String message) {
        super(message);
        this.field = field;
    }
}
