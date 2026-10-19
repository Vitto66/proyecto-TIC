package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos del formulario de inicio de sesión (CU-04). No se valida el formato del correo:
 * uno mal escrito simplemente no coincide con ninguna cuenta y recibe el mismo error genérico.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "ingresá tu correo")
    @Size(max = 150, message = "es demasiado largo")
    private String email;

    @NotBlank(message = "ingresá tu contraseña")
    @Size(max = 72, message = "es demasiado larga")
    private String password;
}
