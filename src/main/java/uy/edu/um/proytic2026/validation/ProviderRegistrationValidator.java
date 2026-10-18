package uy.edu.um.proytic2026.validation;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import uy.edu.um.proytic2026.dto.RegisterProviderRequest;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validaciones del CU-02 que dependen del tipo de persona (5b: RUT o documento con
 * formato inválido → se marca el campo). Las validaciones de los campos comunes
 * están como anotaciones en {@link RegisterProviderRequest}.
 */
@Component
public class ProviderRegistrationValidator implements Validator {

    private static final Pattern DOCUMENT = Pattern.compile("\\d{7,8}");
    private static final Pattern RUT = Pattern.compile("\\d{12}");

    @Override
    public boolean supports(Class<?> clazz) {
        return RegisterProviderRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        RegisterProviderRequest r = (RegisterProviderRequest) target;

        if (r.getPersonType() == null) {
            errors.rejectValue("personType", "required", "elegí persona física o jurídica");
        } else {
            switch (r.getPersonType()) {
                case PHYSICAL -> {
                    requireText(errors, "firstName", r.getFirstName());
                    requireText(errors, "lastName", r.getLastName());
                    requirePattern(errors, "document", r.getDocument(), DOCUMENT,
                            "debe tener 7 u 8 dígitos, sin puntos ni guion");
                }
                case LEGAL -> {
                    requirePattern(errors, "rut", r.getRut(), RUT, "debe tener 12 dígitos, sin puntos ni guiones");
                    requireText(errors, "legalName", r.getLegalName());
                    requireText(errors, "businessName", r.getBusinessName());
                }
            }
        }

        if (r.getConfirmPassword() != null && !Objects.equals(r.getConfirmPassword(), r.getPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "las contraseñas no coinciden");
        }
    }

    private void requireText(Errors errors, String field, String value) {
        if (value == null || value.isBlank()) {
            reject(errors, field, "es obligatorio");
        }
    }

    private void requirePattern(Errors errors, String field, String value, Pattern pattern, String message) {
        if (value == null || value.isBlank()) {
            reject(errors, field, "es obligatorio");
        } else if (!pattern.matcher(value.trim()).matches()) {
            reject(errors, field, message);
        }
    }

    /** Un solo error por campo, para no repetir mensajes con los de las anotaciones. */
    private void reject(Errors errors, String field, String message) {
        if (!errors.hasFieldErrors(field)) {
            errors.rejectValue(field, "invalid", message);
        }
    }
}
