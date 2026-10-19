package uy.edu.um.proytic2026.exception;

public class ProviderNotFoundException extends RuntimeException {
    public ProviderNotFoundException(Long id) {
        super("No existe el prestador " + id);
    }
}
