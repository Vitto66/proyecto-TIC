package uy.edu.um.proytic2026.exception;

/**
 * CU-04 2b: las credenciales son correctas, pero la única cuenta que tiene el correo es la de
 * un prestador RECHAZADO, así que se bloquea el acceso a su espacio de gestión.
 */
public class ProviderRejectedException extends RuntimeException {
    public ProviderRejectedException() {
        super("Tu solicitud como prestador fue rechazada, por eso no podés acceder a tu espacio de gestión");
    }
}
