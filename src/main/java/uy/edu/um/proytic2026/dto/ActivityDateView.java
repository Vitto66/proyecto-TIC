package uy.edu.um.proytic2026.dto;

import java.time.LocalDateTime;

/**
 * Una fecha de una actividad para mostrar en pantalla, con sus cupos.
 *
 * @param maxCapacity   cupo máximo, o null si no tiene límite.
 * @param occupiedSeats lugares ocupados por reservas confirmadas.
 */
public record ActivityDateView(Long id, LocalDateTime startAt, LocalDateTime endAt,
                               Integer maxCapacity, long occupiedSeats) {

    public boolean unlimited() {
        return maxCapacity == null;
    }

    /** Lugares libres, o null si no tiene límite. Nunca negativo. */
    public Long freeSeats() {
        return unlimited() ? null : Math.max(0, maxCapacity - occupiedSeats);
    }

    public boolean full() {
        return !unlimited() && freeSeats() == 0;
    }

    /** "Sábado 25 de octubre de 2026". */
    public String dayLabel() {
        return DateLabels.day(startAt.toLocalDate());
    }

    /** "10:00 a 12:30". */
    public String timeLabel() {
        return DateLabels.time(startAt) + " a " + DateLabels.time(endAt);
    }

    /** "Sin límite de cupos", "Completa" o "8 lugares libres de 12". */
    public String capacityLabel() {
        if (unlimited()) {
            return occupiedSeats == 0 ? "Sin límite de cupos"
                    : "Sin límite de cupos · " + occupiedSeats + (occupiedSeats == 1 ? " reservado" : " reservados");
        }
        if (full()) return "Completa · " + maxCapacity + " de " + maxCapacity + " lugares ocupados";
        long free = freeSeats();
        return free + (free == 1 ? " lugar libre" : " lugares libres") + " de " + maxCapacity;
    }
}
