package uy.edu.um.proytic2026.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los teléfonos van solo con números: 9 para un celular (099123456) u 8 para un fijo (24001234).
 * Vale para el turista (opcional), el emprendimiento y los acompañantes de una reserva.
 */
class PhoneValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"099123456", "24001234"})
    void digitsOnly_8or9_areValid(String phone) {
        assertThat(validator.validateValue(CompanionForm.class, "phone", phone)).isEmpty();
        assertThat(validator.validateValue(RegisterBusinessRequest.class, "phone", phone)).isEmpty();
        assertThat(validator.validateValue(RegisterTouristRequest.class, "phone", phone)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0991234567", "0991234", "099 123 456", "+59899123456", "099abc456", "099-123-456"})
    void lettersSymbolsSpacesOrMoreThan9Digits_areRejected(String phone) {
        assertThat(validator.validateValue(CompanionForm.class, "phone", phone)).isNotEmpty();
        assertThat(validator.validateValue(RegisterBusinessRequest.class, "phone", phone)).isNotEmpty();
        assertThat(validator.validateValue(RegisterTouristRequest.class, "phone", phone)).isNotEmpty();
    }

    @Test
    void touristPhone_isOptional_butTheOthersAreRequired() {
        assertThat(validator.validateValue(RegisterTouristRequest.class, "phone", "")).isEmpty();
        assertThat(validator.validateValue(RegisterTouristRequest.class, "phone", null)).isEmpty();
        assertThat(validator.validateValue(CompanionForm.class, "phone", "")).isNotEmpty();
        assertThat(validator.validateValue(RegisterBusinessRequest.class, "phone", "")).isNotEmpty();
    }
}
