package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Formulario de "Valorar" (CU-18 paso 3): puntuación de 1 a 5 y un comentario opcional. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewRequest {

    /** 3a: si no la elige, se le pide completarla. */
    @NotNull(message = "elegí una puntuación de 1 a 5 estrellas")
    @Min(value = 1, message = "elegí una puntuación de 1 a 5 estrellas")
    @Max(value = 5, message = "elegí una puntuación de 1 a 5 estrellas")
    private Integer rating;

    @Size(max = ReviewRequest.MAX_COMMENT, message = "puede tener hasta 1000 caracteres")
    private String comment;

    public static final int MAX_COMMENT = 1000;

    /** El comentario sin espacios de más; null si lo dejó vacío. */
    public String cleanComment() {
        if (comment == null) return null;
        String trimmed = comment.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
