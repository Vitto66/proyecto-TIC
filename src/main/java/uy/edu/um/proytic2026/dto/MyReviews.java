package uy.edu.um.proytic2026.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-24 "Mis valoraciones": lo que le falta valorar al turista y lo que ya valoró.
 *
 * @param pending reservas finalizadas sin valoración, de la más reciente a la más vieja (paso 2).
 * @param done    sus valoraciones, de la más nueva a la más vieja (paso 3).
 */
public record MyReviews(List<ToReview> pending, List<Done> done) {

    public boolean empty() {
        return pending.isEmpty() && done.isEmpty();
    }

    /** Una reserva finalizada que todavía puede valorar (CU-18). */
    public record ToReview(Long reservationId, String activityName, String businessName, LocalDateTime startAt) {

        public String dayLabel() {
            return DateLabels.day(startAt.toLocalDate());
        }
    }

    /**
     * Una valoración que ya hizo.
     *
     * @param activityActive si la actividad sigue publicada (si la dieron de baja no se enlaza su página).
     */
    public record Done(Long reservationId, Long activityId, String activityName, String businessName,
                       boolean activityActive, LocalDateTime startAt, ReviewView review) {

        public String dayLabel() {
            return DateLabels.day(startAt.toLocalDate());
        }
    }
}
