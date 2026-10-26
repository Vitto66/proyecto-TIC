package uy.edu.um.proytic2026.exception;

public class TypeNotFoundException extends RuntimeException {
    public TypeNotFoundException(Integer id) {
        super("No existe el tipo " + id);
    }
}
