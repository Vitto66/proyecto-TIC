package uy.edu.um.proytic2026.validation;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import uy.edu.um.proytic2026.dto.RegisterProviderRequest;
import uy.edu.um.proytic2026.entity.PersonType;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitarios de las validaciones del CU-02 que dependen del tipo de persona. */
class ProviderRegistrationValidatorTest {

    private final ProviderRegistrationValidator validator = new ProviderRegistrationValidator();

    private Errors validate(RegisterProviderRequest request) {
        Errors errors = new BeanPropertyBindingResult(request, "form");
        validator.validate(request, errors);
        return errors;
    }

    private RegisterProviderRequest.RegisterProviderRequestBuilder base() {
        return RegisterProviderRequest.builder()
                .address("Calle 1").email("a@b.uy").password("secreta123");
    }

    @Test
    void validPhysicalPerson_hasNoErrors() {
        Errors errors = validate(base().personType(PersonType.PHYSICAL)
                .firstName("Juan").lastName("Rodríguez").document("4567890").build());
        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void validLegalPerson_hasNoErrors() {
        Errors errors = validate(base().personType(PersonType.LEGAL)
                .rut("211234560018").legalName("Empresa S.A.").businessName("Kayak").build());
        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void missingPersonType_isRejected() {
        Errors errors = validate(base().build());
        assertThat(errors.hasFieldErrors("personType")).isTrue();
    }

    @Test
    void physicalPerson_requiresNamesAndValidDocument() {
        Errors errors = validate(base().personType(PersonType.PHYSICAL).document("4.567.890-1").build());
        assertThat(errors.hasFieldErrors("firstName")).isTrue();
        assertThat(errors.hasFieldErrors("lastName")).isTrue();
        assertThat(errors.getFieldError("document").getDefaultMessage()).contains("7 u 8 dígitos");
        // los datos de persona jurídica no se piden
        assertThat(errors.hasFieldErrors("rut")).isFalse();
    }

    @Test
    void legalPerson_requiresTwelveDigitRut() {
        Errors errors = validate(base().personType(PersonType.LEGAL)
                .rut("21123456").legalName("Empresa S.A.").businessName("Kayak").build());
        assertThat(errors.getFieldError("rut").getDefaultMessage()).contains("12 dígitos");
        assertThat(errors.hasFieldErrors("document")).isFalse();
    }

    @Test
    void legalPerson_requiresLegalNameAndBusinessName() {
        Errors errors = validate(base().personType(PersonType.LEGAL).rut("211234560018").build());
        assertThat(errors.hasFieldErrors("legalName")).isTrue();
        assertThat(errors.hasFieldErrors("businessName")).isTrue();
    }

    @Test
    void confirmPasswordMismatch_isRejected() {
        Errors errors = validate(base().personType(PersonType.PHYSICAL)
                .firstName("Juan").lastName("R").document("4567890").confirmPassword("otra").build());
        assertThat(errors.hasFieldErrors("confirmPassword")).isTrue();
    }

    @Test
    void confirmPasswordAbsent_isAcceptedForApiClients() {
        Errors errors = validate(base().personType(PersonType.PHYSICAL)
                .firstName("Juan").lastName("R").document("4567890").confirmPassword(null).build());
        assertThat(errors.hasFieldErrors("confirmPassword")).isFalse();
    }
}
