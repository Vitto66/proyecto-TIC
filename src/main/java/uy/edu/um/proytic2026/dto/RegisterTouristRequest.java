package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String password;

    /** Solo lo manda el formulario web. Si viene, tiene que coincidir con password. */
    private String confirmPassword;

    /** Opcional. */
    @Size(max = 30)
    private String phone;
}
