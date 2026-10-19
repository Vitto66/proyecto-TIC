package uy.edu.um.proytic2026.validation;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;

import java.util.Objects;

/**
 * Validaciones del CU-01 que no se pueden expresar con anotaciones en un solo campo.
 * Hoy: que la confirmación de la contraseña coincida (solo la manda el formulario web).
 */
@Component
public class TouristRegistrationValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return RegisterTouristRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        RegisterTouristRequest r = (RegisterTouristRequest) target;
        if (r.getConfirmPassword() != null && !Objects.equals(r.getConfirmPassword(), r.getPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "las contraseñas no coinciden");
        }
    }
}
