package uy.edu.um.proytic2026.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una actividad en "Lo más próximo" del inicio: su próxima fecha con lugar y que todavía se
 * puede reservar.
 *
 * @param freeSeats lugares libres, o null si la fecha no tiene cupo máximo.
 * @param today     el día de hoy, para decir "Hoy" o "Mañana".
 */
public record UpcomingActivity(HomeActivity activity, LocalDateTime startAt, Long freeSeats, LocalDate today) {

    /** "Hoy", "Mañana" o "Sáb 31 oct". */
    public String dayLabel() {
        LocalDate day = startAt.toLocalDate();
        if (day.equals(today)) return "Hoy";
        if (day.equals(today.plusDays(1))) return "Mañana";
        return DateLabels.shortDay(day);
    }

    /** "10:30". */
    public String timeLabel() {
        return DateLabels.time(startAt);
    }

    /** El número del día, para el calendarito de la tarjeta. */
    public int dayNumber() {
        return startAt.getDayOfMonth();
    }

    /** "oct", para el calendarito de la tarjeta. */
    public String monthShort() {
        String[] parts = DateLabels.shortDay(startAt.toLocalDate()).split(" ");
        return parts[parts.length - 1].toLowerCase();
    }

    /** Pocos lugares: se resalta. */
    public boolean fewSeats() {
        return freeSeats != null && freeSeats <= 3;
    }

    /** "Queda 1 lugar", "Quedan 5 lugares" o "Lugares disponibles" si no tiene cupo máximo. */
    public String seatsLabel() {
        if (freeSeats == null) return "Lugares disponibles";
        return freeSeats == 1 ? "Queda 1 lugar" : "Quedan " + freeSeats + " lugares";
    }
}
