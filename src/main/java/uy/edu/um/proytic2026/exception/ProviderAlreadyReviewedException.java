package uy.edu.um.proytic2026.exception;

/**
 * CU-03: solo se aprueba o rechaza un prestador PENDIENTE. Pasa, por ejemplo, si otro
 * administrador ya lo resolvió mientras este tenía la página abierta.
 */
public class ProviderAlreadyReviewedException extends RuntimeException {
    public ProviderAlreadyReviewedException() {
        super("Este prestador ya fue revisado por un administrador");
    }
}
