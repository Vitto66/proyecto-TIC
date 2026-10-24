package uy.edu.um.proytic2026.dto;

import java.util.List;

/**
 * Resultado de agregar una fecha (CU-09): o se guardó, o se superpone con otras fechas de la
 * misma actividad y el prestador tiene que confirmar (3b).
 */
public record AddDateResult(ActivityDateView saved, List<ActivityDateView> overlaps) {

    public static AddDateResult saved(ActivityDateView date) {
        return new AddDateResult(date, List.of());
    }

    public static AddDateResult needsConfirmation(List<ActivityDateView> overlaps) {
        return new AddDateResult(null, overlaps);
    }

    public boolean needsConfirmation() {
        return saved == null;
    }
}
