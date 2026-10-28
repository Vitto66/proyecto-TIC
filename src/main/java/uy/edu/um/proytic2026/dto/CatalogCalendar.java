package uy.edu.um.proytic2026.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * La vista de calendario de la vidriera (CU-05): un mes, de lunes a domingo, con las fechas
 * disponibles (futuras y con lugar) de las actividades que cumplen los filtros.
 *
 * @param month      el mes que se muestra, "2026-11".
 * @param prevMonth  el mes anterior, para la flecha ‹; null si sería un mes que ya pasó.
 * @param totalDates cuántas fechas disponibles hay en el mes.
 */
public record CatalogCalendar(String month, String monthLabel, String prevMonth, String nextMonth,
                              List<List<Day>> weeks, int totalDates) {

    /** Cuántas fechas se muestran adentro de cada día; las demás se resumen en "+N más". */
    public static final int SHOWN_PER_DAY = 3;

    public static final List<String> WEEKDAYS = List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom");

    public List<String> weekdays() {
        return WEEKDAYS;
    }

    public static CatalogCalendar of(YearMonth month, YearMonth current, List<List<Day>> weeks, int totalDates) {
        return new CatalogCalendar(month.toString(), DateLabels.month(month),
                month.isAfter(current) ? month.minusMonths(1).toString() : null,
                month.plusMonths(1).toString(), weeks, totalDates);
    }

    /**
     * Un día del calendario.
     *
     * @param inMonth si es del mes que se muestra (los de los bordes completan la semana).
     */
    public record Day(LocalDate date, boolean inMonth, boolean today, List<Slot> slots) {

        public int number() {
            return date.getDayOfMonth();
        }

        public boolean hasSlots() {
            return !slots.isEmpty();
        }

        public List<Slot> shown() {
            return slots.size() <= SHOWN_PER_DAY ? slots : slots.subList(0, SHOWN_PER_DAY);
        }

        public int hiddenCount() {
            return Math.max(0, slots.size() - SHOWN_PER_DAY);
        }

        /** "Sábado 25 de octubre de 2026", para leer el día completo. */
        public String label() {
            return DateLabels.day(date);
        }
    }

    /**
     * Una fecha disponible de una actividad.
     *
     * @param freeSeats lugares libres, o null si no tiene límite.
     */
    public record Slot(Long activityId, Long instanceId, String activityName, String time, Long freeSeats,
                       String scene) {

        public String freeLabel() {
            if (freeSeats == null) return "Con lugar";
            return freeSeats == 1 ? "1 lugar" : freeSeats + " lugares";
        }
    }
}
