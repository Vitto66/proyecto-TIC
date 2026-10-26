package uy.edu.um.proytic2026.dto;

import java.util.List;

/**
 * CU-11 paso 2: las fechas de las actividades del prestador, separadas en próximas y pasadas.
 *
 * @param totalReservations reservas vigentes entre todas las fechas. Si es 0 se informa (2a).
 */
public record ProviderReservations(List<ProviderDateRow> upcoming, List<ProviderDateRow> past,
                                   long totalReservations) {

    public boolean hasDates() {
        return !upcoming.isEmpty() || !past.isEmpty();
    }
}
