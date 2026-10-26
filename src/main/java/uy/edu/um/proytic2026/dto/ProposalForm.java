package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uy.edu.um.proytic2026.entity.TypeCategory;

/** CU-12 paso 2: el tipo que propone el prestador. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProposalForm {

    @NotNull(message = "elegí si es un tipo de actividad o de equipamiento")
    private TypeCategory category;

    @NotBlank(message = "es obligatorio")
    @Size(max = 100, message = "puede tener hasta 100 caracteres")
    private String name;

    @NotBlank(message = "contale al administrador para qué lo necesitás")
    @Size(max = 500, message = "puede tener hasta 500 caracteres")
    private String description;
}
