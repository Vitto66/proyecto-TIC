package uy.edu.um.proytic2026.exception;

public class ProposalNotFoundException extends RuntimeException {
    public ProposalNotFoundException() {
        super("No existe esa propuesta");
    }
}
