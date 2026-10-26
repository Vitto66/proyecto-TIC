package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos de un acompañante en el formulario de reserva (CU-14 paso 3). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanionForm {

    @NotBlank(message = "es obligatorio")
    @Size(max = 100, message = "puede tener hasta 100 caracteres")
    private String firstName;

    @NotBlank(message = "es obligatorio")
    @Size(max = 100, message = "puede tener hasta 100 caracteres")
    private String lastName;

    @NotBlank(message = "es obligatoria")
    @Pattern(regexp = "\\d{7,8}", message = "debe tener 7 u 8 dígitos, sin puntos ni guion")
    private String nationalId;

    /** Celular o fijo: dígitos, espacios y un + al principio. Ej.: 099 123 456 o +598 99 123 456. */
    @NotBlank(message = "es obligatorio")
    @Pattern(regexp = "\\+?[0-9 ]{8,20}", message = "ingresá un teléfono válido, por ejemplo 099 123 456")
    private String phone;

    /** Sin espacios de más, para guardar y comparar. */
    public CompanionForm trimmed() {
        return new CompanionForm(trim(firstName), trim(lastName), trim(nationalId),
                phone == null ? null : phone.trim().replaceAll("\\s+", " "));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
