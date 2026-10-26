package uy.edu.um.proytic2026.exception;

public class ProposalNotAllowedException extends RuntimeException {
    public ProposalNotAllowedException() {
        super("Tu cuenta de prestador tiene que estar aprobada para proponer tipos");
    }
}
