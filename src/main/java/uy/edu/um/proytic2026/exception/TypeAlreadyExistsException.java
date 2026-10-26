package uy.edu.um.proytic2026.exception;

import lombok.Getter;

/** CU-12 2a: ya existe un tipo con ese nombre; se informa y se ofrece el existente. */
@Getter
public class TypeAlreadyExistsException extends RuntimeException {

    private final String existingName;
    private final boolean active;

    public TypeAlreadyExistsException(String existingName, boolean active) {
        super("Ya existe \"" + existingName + "\"");
        this.existingName = existingName;
        this.active = active;
    }
}
