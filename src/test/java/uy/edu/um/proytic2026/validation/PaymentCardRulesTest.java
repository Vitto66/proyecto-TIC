package uy.edu.um.proytic2026.validation;

import org.junit.jupiter.api.Test;
import uy.edu.um.proytic2026.entity.CardBrand;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitarios del formato de tarjeta usado en el medio de pago de CU-01. */
class PaymentCardRulesTest {

    @Test
    void validNumber_passesLuhnCheck_evenWithSpacesOrDashes() {
        assertThat(PaymentCardRules.isValidNumber("4242424242424242")).isTrue();
        assertThat(PaymentCardRules.isValidNumber("4242 4242 4242 4242")).isTrue();
        assertThat(PaymentCardRules.isValidNumber("5555-5555-5555-4444")).isTrue();
    }

    @Test
    void invalidNumber_isRejected() {
        assertThat(PaymentCardRules.isValidNumber("4242424242424241")).isFalse(); // dígito verificador mal
        assertThat(PaymentCardRules.isValidNumber("1234")).isFalse();             // muy corto
        assertThat(PaymentCardRules.isValidNumber("4242abcd42424242")).isFalse();
        assertThat(PaymentCardRules.isValidNumber(null)).isFalse();
    }

    @Test
    void brand_isDetectedFromFirstDigits() {
        assertThat(CardBrand.detect("4242424242424242")).isEqualTo(CardBrand.VISA);
        assertThat(CardBrand.detect("5555555555554444")).isEqualTo(CardBrand.MASTERCARD);
        assertThat(CardBrand.detect("2223003122003222")).isEqualTo(CardBrand.MASTERCARD);
        assertThat(CardBrand.detect("378282246310005")).isEqualTo(CardBrand.AMEX);
        assertThat(CardBrand.detect("6011111111111117")).isEqualTo(CardBrand.OTHER);
    }

    @Test
    void expiry_acceptsTwoOrFourDigitYear() {
        assertThat(PaymentCardRules.parseExpiry("08/29")).isEqualTo(YearMonth.of(2029, 8));
        assertThat(PaymentCardRules.parseExpiry("08 / 2029")).isEqualTo(YearMonth.of(2029, 8));
        assertThat(PaymentCardRules.parseExpiry("13/29")).isNull();
        assertThat(PaymentCardRules.parseExpiry("0829")).isNull();
    }

    @Test
    void cvv_hasThreeOrFourDigits() {
        assertThat(PaymentCardRules.isValidCvv("123")).isTrue();
        assertThat(PaymentCardRules.isValidCvv("1234")).isTrue();
        assertThat(PaymentCardRules.isValidCvv("12")).isFalse();
        assertThat(PaymentCardRules.isValidCvv("12a")).isFalse();
    }

    @Test
    void last4_ignoresSeparators() {
        assertThat(PaymentCardRules.last4("4242 4242 4242 4242")).isEqualTo("4242");
    }
}
