package uy.edu.um.proytic2026.dto;

/**
 * Una próxima fecha en el detalle de una actividad (CU-06 paso 3), con su pronóstico (paso 5).
 *
 * @param bookingClosed ya pasó el límite de minutos previos al inicio: no se admiten reservas.
 * @param forecast      pronóstico de ese día, o null si no está disponible (5a).
 */
public record DetailDate(ActivityDateView date, boolean bookingClosed, DailyForecast forecast) {

    /** Se puede reservar: tiene lugar y todavía no cerraron las reservas. */
    public boolean available() {
        return !date.full() && !bookingClosed;
    }

    /** "8 lugares libres de 12", "Completa", "Reservas cerradas" o "Sin límite de cupos". */
    public String statusLabel() {
        if (bookingClosed) return "Reservas cerradas";
        if (date.full()) return "Completa";
        if (date.unlimited()) return "Lugares disponibles";
        long free = date.freeSeats();
        return free + (free == 1 ? " lugar libre" : " lugares libres");
    }

    public DetailDate withForecast(DailyForecast dayForecast) {
        return new DetailDate(date, bookingClosed, dayForecast);
    }
}
