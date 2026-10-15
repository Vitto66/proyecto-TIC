package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos de entrada del CU-01 Registrarse como turista. */
public record RegisterTouristRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Pattern(regexp = "\\d{7,8}", message = "debe tener 7 u 8 dígitos, sin puntos ni guion")
        String nationalId,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres") String password,
        @Size(max = 30) String phone
) {
}
