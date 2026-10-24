package uy.edu.um.proytic2026.dto;

import java.util.List;

/**
 * Las fechas de una actividad del prestador (CU-09): las próximas, de la más cercana a la más
 * lejana, y las que ya pasaron, de la más reciente a la más vieja.
 */
public record ActivitySchedule(Long activityId, String activityName, int durationMinutes, boolean active,
                               List<ActivityDateView> upcoming, List<ActivityDateView> past) {

    public String durationLabel() {
        return ActivitySummary.formatMinutes(durationMinutes);
    }
}
