package uy.edu.um.proytic2026.validation;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Validaciones del CU-01 que no se pueden expresar con anotaciones en un solo campo:
 * <ul>
 *   <li>que la confirmación de la contraseña coincida (solo la manda el formulario web);</li>
 *   <li>el medio de pago opcional (paso 3): si se completa algún dato, se piden los tres y
 *       tienen que ser válidos (4b: datos inválidos → se marcan los campos).</li>
 * </ul>
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
        if (r.wantsPaymentMethod()) {
            validatePaymentMethod(r, errors);
        }
    }

    private void validatePaymentMethod(RegisterTouristRequest r, Errors errors) {
        if (isBlank(r.getCardNumber())) {
            reject(errors, "cardNumber", "es obligatorio si agregás un medio de pago");
        } else if (!PaymentCardRules.isValidNumber(r.getCardNumber())) {
            reject(errors, "cardNumber", "no es un número de tarjeta válido");
        }

        if (isBlank(r.getCardExpiry())) {
            reject(errors, "cardExpiry", "es obligatorio si agregás un medio de pago");
        } else {
            YearMonth expiry = PaymentCardRules.parseExpiry(r.getCardExpiry());
            if (expiry == null) {
                reject(errors, "cardExpiry", "tiene que tener el formato MM/AA");
            } else if (expiry.isBefore(YearMonth.now())) {
                reject(errors, "cardExpiry", "la tarjeta está vencida");
            }
        }

        if (isBlank(r.getCardCvv())) {
            reject(errors, "cardCvv", "es obligatorio si agregás un medio de pago");
        } else if (!PaymentCardRules.isValidCvv(r.getCardCvv())) {
            reject(errors, "cardCvv", "tiene que tener 3 o 4 dígitos");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Un solo error por campo, para no repetir mensajes con los de las anotaciones. */
    private static void reject(Errors errors, String field, String message) {
        if (!errors.hasFieldErrors(field)) {
            errors.rejectValue(field, "invalid", message);
        }
    }
}
