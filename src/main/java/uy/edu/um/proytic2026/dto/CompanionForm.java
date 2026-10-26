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

    /** Celular (9 números, ej. 099123456) o fijo (8 números, ej. 24001234): solo dígitos. */
    @NotBlank(message = "es obligatorio")
    @Pattern(regexp = "\\d{8,9}", message = "tiene que tener 8 o 9 números, sin espacios")
    private String phone;

    /** Sin espacios de más, para guardar y comparar. */
    public CompanionForm trimmed() {
        return new CompanionForm(trim(firstName), trim(lastName), trim(nationalId), trim(phone));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
