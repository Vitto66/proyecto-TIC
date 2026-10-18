package uy.edu.um.proytic2026.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    /** @param role el perfil que ya tiene ese correo, en español ("turista", "prestador"). */
    public EmailAlreadyRegisteredException(String email, String role) {
        super("El correo " + email + " ya está registrado como " + role);
    }
}
