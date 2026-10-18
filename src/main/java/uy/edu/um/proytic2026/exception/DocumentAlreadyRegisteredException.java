package uy.edu.um.proytic2026.exception;

import lombok.Getter;

/** El documento o el RUT de un prestador ya pertenece a otro prestador. */
@Getter
public class DocumentAlreadyRegisteredException extends RuntimeException {

    /** Campo del formulario al que corresponde el error ("document" o "rut"). */
    private final String field;

    public DocumentAlreadyRegisteredException(String field, String message) {
        super(message);
        this.field = field;
    }
}
