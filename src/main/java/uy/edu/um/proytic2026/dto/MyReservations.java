package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-17 "Mis reservas": el calendario de un mes con las reservas del turista, los filtros y la
 * lista de lo que se está mirando (todo el mes o un día).
 *
 * @param hasAny        si el turista tiene al menos una reserva (2a: si no, se ofrece explorar).
 * @param typeId        el tipo de actividad elegido en el filtro, o null para todos.
 * @param selectedDay   el día elegido en el calendario, o null para ver todo el mes.
 * @param weeks         el calendario: semanas de lunes a domingo, con los días de los meses vecinos.
 * @param entries       la lista debajo del calendario, con los filtros aplicados.
 * @param next          la próxima reserva pendiente o confirmada (para ir a su mes), o null.
 */
public record MyReservations(boolean hasAny, String monthParam, String monthLabel,
                             String previousMonthHref, String nextMonthHref, String wholeMonthHref,
                             Integer typeId, List<TypeOption> types,
                             LocalDate selectedDay, List<List<CalendarDay>> weeks,
                             List<Entry> entries, Entry next, String nextHref) {

    public static final List<String> WEEKDAYS = List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom");

    public List<String> weekdays() {
        return WEEKDAYS;
    }

    public boolean filtered() {
        return typeId != null;
    }

    /** "Reservas de noviembre de 2026" o "Reservas del domingo 1 de noviembre de 2026". */
    public String listTitle() {
        return selectedDay == null ? "Reservas de " + monthLabel.toLowerCase()
                : "Reservas del " + DateLabels.day(selectedDay).toLowerCase();
    }

    /** Un tipo de actividad para el filtro: solo los de las actividades que reservó. */
    public record TypeOption(Integer id, String name) {
    }

    /** Un día del calendario. href es null si ese día no tiene reservas (no se puede elegir). */
    public record CalendarDay(LocalDate date, boolean inMonth, boolean today, boolean selected,
                              List<Entry> entries, String href) {

        public int dayOfMonth() {
            return date.getDayOfMonth();
        }

        public boolean hasEntries() {
            return !entries.isEmpty();
        }

        /** Para lectores de pantalla: "Domingo 1 de noviembre de 2026, 2 reservas". */
        public String ariaLabel() {
            String day = DateLabels.day(date);
            return entries.isEmpty() ? day
                    : day + ", " + entries.size() + (entries.size() == 1 ? " reserva" : " reservas");
        }
    }

    /**
     * Una reserva en el calendario y en la lista.
     *
     * @param payable    4a: está pendiente y todavía se puede pagar.
     * @param toReview   4b: está finalizada y todavía no la valoró.
     */
    public record Entry(Long id, String activityName, String businessName, LocalDateTime startAt,
                        LocalDateTime endAt, int participants, String totalLabel, ReservationStatus status,
                        boolean payable, boolean toReview) {

        public String dayLabel() {
            return DateLabels.day(startAt.toLocalDate());
        }

        public String timeLabel() {
            return DateLabels.time(startAt) + " a " + DateLabels.time(endAt);
        }

        public String startLabel() {
            return DateLabels.time(startAt);
        }

        public String participantsLabel() {
            return participants + (participants == 1 ? " persona" : " personas");
        }

        public String statusLabel() {
            return ReservationView.statusLabel(status);
        }

        /** La clase de color del estado (reserve.css / my-reservations.css). */
        public String statusClass() {
            return "is-" + status.name().toLowerCase();
        }
    }
}
