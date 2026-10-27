package uy.edu.um.proytic2026.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones del dominio a respuestas HTTP con un formato uniforme.
 * Aplica solo a la API REST: las páginas web (Thymeleaf) muestran los errores en el formulario.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    @ExceptionHandler({EmailAlreadyRegisteredException.class,
            NationalIdAlreadyRegisteredException.class,
            DocumentAlreadyRegisteredException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Dos registros simultáneos con el mismo dato: la restricción UNIQUE de la base lo frena. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Ya existe un registro con esos datos");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** CU-01 4b / CU-02 5b: datos inválidos → se informan los campos con error. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        problem.setProperty("fields", fields);
        return problem;
    }

    /** API para otras plataformas (RF-10): un parámetro incorrecto → 400 indicando cuál. */
    @ExceptionHandler(InvalidApiParameterException.class)
    public ProblemDetail handleInvalidApiParameter(InvalidApiParameterException ex) {
        return badParameter(ex.getParameter(), ex.getMessage());
    }

    /** Falta un parámetro obligatorio, por ejemplo la fecha en CU-20. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        return badParameter(ex.getParameterName(), "Falta el parámetro " + ex.getParameterName());
    }

    /** Un parámetro con formato inválido, por ejemplo date=31-12-2026 o departmentId=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badParameter(ex.getName(), "El parámetro " + ex.getName() + " tiene un formato inválido");
    }

    private ProblemDetail badParameter(String parameter, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("parameter", parameter);
        return problem;
    }
}
