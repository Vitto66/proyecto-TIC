package uy.edu.um.proytic2026.exception;

/** La foto de la actividad no es una imagen JPG, PNG o WebP, o pesa demasiado. */
public class InvalidPhotoException extends RuntimeException {
    public InvalidPhotoException(String message) {
        super(message);
    }
}
