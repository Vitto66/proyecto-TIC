package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Datos de entrada del CU-01 Registrarse como turista.
 * <p>
 * Se usa tanto en la API REST (JSON) como en el formulario web (Thymeleaf), por eso es
 * una clase con getters y setters y no un record (igual que RegisterProviderRequest).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterTouristRequest {

    @NotBlank(message = "es obligatorio")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "es obligatorio")
    @Size(max = 100)
    private String lastName;

    /** Cédula, sin puntos ni guion. */
    @NotBlank(message = "es obligatoria")
    @Pattern(regexp = "\\d{7,8}", message = "debe tener 7 u 8 dígitos, sin puntos ni guion")
    private String nationalId;

    @NotBlank(message = "es obligatorio")
    @Email(message = "no es un correo válido")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "es obligatoria")
    @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
    @ToString.Exclude
    private String password;

    /** Solo lo manda el formulario web. Si viene, tiene que coincidir con password. */
    @ToString.Exclude
    private String confirmPassword;

    /** Opcional. */
    @Size(max = 30)
    private String phone;

    // --- Medio de pago, opcional (CU-01 paso 3). Si se completa uno, se piden los tres. ---
    // Lo valida TouristRegistrationValidator. El número y el CVV nunca se guardan ni se loguean.

    /** Número de tarjeta; se aceptan espacios o guiones entre los grupos. */
    @Size(max = 25)
    @ToString.Exclude
    private String cardNumber;

    /** Vencimiento, "MM/AA". */
    @Size(max = 7)
    private String cardExpiry;

    /** Código de seguridad, 3 o 4 dígitos. */
    @Size(max = 4)
    @ToString.Exclude
    private String cardCvv;

    /** true si el turista quiso registrar un medio de pago (completó al menos un dato). */
    public boolean wantsPaymentMethod() {
        return hasText(cardNumber) || hasText(cardExpiry) || hasText(cardCvv);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
