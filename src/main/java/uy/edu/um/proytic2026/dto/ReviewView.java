package uy.edu.um.proytic2026.dto;

import java.time.LocalDateTime;

/**
 * Una valoración en el detalle de una actividad (CU-06 paso 4).
 *
 * @param author nombre y la inicial del apellido del turista ("Ana P."): no se muestra completo.
 */
public record ReviewView(String author, int rating, String comment, LocalDateTime createdAt) {

    /** "★★★★☆". */
    public String stars() {
        return "★".repeat(rating) + "☆".repeat(Math.max(0, 5 - rating));
    }

    public boolean hasComment() {
        return comment != null && !comment.isBlank();
    }

    public String dateLabel() {
        return DateLabels.day(createdAt.toLocalDate());
    }
}
