package uy.edu.um.proytic2026.validation;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitarios de las validaciones del CU-01 que no son anotaciones de un solo campo. */
class TouristRegistrationValidatorTest {

    private final TouristRegistrationValidator validator = new TouristRegistrationValidator();

    /** Un vencimiento que siempre está en el futuro, sin importar cuándo se corra el test. */
    private static final String FUTURE_EXPIRY = YearMonth.now().plusYears(3)
            .format(java.time.format.DateTimeFormatter.ofPattern("MM/yy"));

    private Errors validate(RegisterTouristRequest request) {
        Errors errors = new BeanPropertyBindingResult(request, "form");
        validator.validate(request, errors);
        return errors;
    }

    private RegisterTouristRequest.RegisterTouristRequestBuilder base() {
        return RegisterTouristRequest.builder()
                .firstName("Ana").lastName("Pérez").nationalId("12345678")
                .email("ana@mail.com").password("secreta123");
    }

    @Test
    void withoutPaymentMethod_hasNoErrors() {
        assertThat(validate(base().build()).hasErrors()).isFalse();
    }

    @Test
    void validPaymentMethod_hasNoErrors() {
        Errors errors = validate(base()
                .cardNumber("4242 4242 4242 4242").cardExpiry(FUTURE_EXPIRY).cardCvv("123").build());
        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void partialPaymentMethod_asksForTheMissingFields() {
        Errors errors = validate(base().cardNumber("4242424242424242").build());
        assertThat(errors.hasFieldErrors("cardNumber")).isFalse();
        assertThat(errors.hasFieldErrors("cardExpiry")).isTrue();
        assertThat(errors.hasFieldErrors("cardCvv")).isTrue();
    }

    @Test
    void invalidCardNumber_isMarked() {
        Errors errors = validate(base()
                .cardNumber("4242424242424241").cardExpiry(FUTURE_EXPIRY).cardCvv("123").build());
        assertThat(errors.getFieldError("cardNumber").getDefaultMessage()).contains("no es un número de tarjeta válido");
    }

    @Test
    void expiredCard_isMarked() {
        String lastMonth = YearMonth.now().minusMonths(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("MM/yy"));
        Errors errors = validate(base()
                .cardNumber("4242424242424242").cardExpiry(lastMonth).cardCvv("123").build());
        assertThat(errors.getFieldError("cardExpiry").getDefaultMessage()).contains("vencida");
    }

    @Test
    void badExpiryFormatAndCvv_areMarked() {
        Errors errors = validate(base()
                .cardNumber("4242424242424242").cardExpiry("2029-08").cardCvv("12").build());
        assertThat(errors.hasFieldErrors("cardExpiry")).isTrue();
        assertThat(errors.hasFieldErrors("cardCvv")).isTrue();
    }

    @Test
    void confirmPasswordMismatch_isMarked() {
        Errors errors = validate(base().confirmPassword("otra").build());
        assertThat(errors.hasFieldErrors("confirmPassword")).isTrue();
    }
}
