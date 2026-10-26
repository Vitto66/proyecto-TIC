package uy.edu.um.proytic2026.exception;

public class ProposalAlreadyResolvedException extends RuntimeException {
    public ProposalAlreadyResolvedException() {
        super("Esta propuesta ya fue resuelta por otro administrador");
    }
}
