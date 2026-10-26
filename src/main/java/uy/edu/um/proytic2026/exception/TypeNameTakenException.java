package uy.edu.um.proytic2026.exception;

/** CU-13 3b: ya existe un tipo con ese nombre (sin distinguir mayúsculas). */
public class TypeNameTakenException extends RuntimeException {
    public TypeNameTakenException(String name) {
        super("Ya existe un tipo llamado \"" + name + "\"");
    }
}
