package uy.edu.um.proytic2026.exception;

public class ProposalAlreadyPendingException extends RuntimeException {
    public ProposalAlreadyPendingException() {
        super("Ya propusiste ese tipo y está pendiente de revisión");
    }
}
