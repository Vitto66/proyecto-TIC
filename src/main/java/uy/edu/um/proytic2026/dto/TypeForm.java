package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nombre de un tipo de actividad o de equipamiento, para darlo de alta o modificarlo (CU-13). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypeForm {

    @NotBlank(message = "es obligatorio")
    @Size(max = 100, message = "puede tener hasta 100 caracteres")
    private String name;
}
