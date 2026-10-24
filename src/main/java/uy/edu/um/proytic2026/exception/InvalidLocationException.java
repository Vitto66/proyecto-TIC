package uy.edu.um.proytic2026.exception;

/**
 * La localidad no existe o no pertenece al departamento elegido. Puede pasar si se manipula
 * el formulario, porque los valores llegan desde el navegador.
 */
public class InvalidLocationException extends RuntimeException {
    public InvalidLocationException() {
        super("La localidad elegida no corresponde al departamento");
    }
}
