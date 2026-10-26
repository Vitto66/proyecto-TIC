package uy.edu.um.proytic2026.dto;

/**
 * Una fecha de una actividad del prestador en la lista de reservas (CU-11 paso 2).
 *
 * @param date         fecha, horario y cupos ocupados y libres.
 * @param reservations reservas vigentes de esa fecha (pendientes, confirmadas o finalizadas).
 */
public record ProviderDateRow(Long activityId, String activityName, ActivityDateView date, long reservations) {

    public String reservationsLabel() {
        if (reservations == 0) return "Sin reservas";
        return reservations == 1 ? "1 reserva" : reservations + " reservas";
    }
}
