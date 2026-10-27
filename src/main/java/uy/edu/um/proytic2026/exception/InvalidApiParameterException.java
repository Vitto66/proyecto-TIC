package uy.edu.um.proytic2026.exception;

import lombok.Getter;

/**
 * Un parámetro de la API para otras plataformas (RF-10) es incorrecto: falta, tiene mal formato
 * o hace referencia a algo que no existe. La respuesta indica cuál es (CU-20 2a, CU-21 1a).
 */
@Getter
public class InvalidApiParameterException extends RuntimeException {

    private final String parameter;

    public InvalidApiParameterException(String parameter, String message) {
        super(message);
        this.parameter = parameter;
    }
}
