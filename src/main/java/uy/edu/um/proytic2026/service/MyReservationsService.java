package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.DateLabels;
import uy.edu.um.proytic2026.dto.Money;
import uy.edu.um.proytic2026.dto.MyReservations;
import uy.edu.um.proytic2026.dto.MyReservations.CalendarDay;
import uy.edu.um.proytic2026.dto.MyReservations.Entry;
import uy.edu.um.proytic2026.dto.MyReservations.TypeOption;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * CU-17 Consultar mis reservas. Arma el calendario de un mes con las reservas del turista y su
 * estado (paso 2), aplica los filtros por fecha y por tipo de actividad (paso 3) y deja cada
 * reserva enlazada a su detalle (paso 4). Es solo una consulta: no cambia nada.
 */
@Service
@RequiredArgsConstructor
public class MyReservationsService {

    static final String PATH = "/tourist/reservations";

    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final Clock clock;

    /**
     * @param month  el mes a mostrar; null = el mes de hoy (o el del día elegido).
     * @param day    un día para ver solo sus reservas; null = todo el mes.
     * @param typeId un tipo de actividad para filtrar; null = todos.
     */
    @Transactional(readOnly = true)
    public MyReservations overview(Long touristId, YearMonth month, LocalDate day, Integer typeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        if (day != null) {
            month = YearMonth.from(day); // el día manda: se muestra su mes
        }
        if (month == null) {
            month = YearMonth.from(today);
        }

        List<Reservation> all = reservationRepository.findAllOfTourist(touristId);
        Set<Long> reviewed = all.isEmpty() ? Set.of()
                : new HashSet<>(reviewRepository.findReviewedReservationIds(all.stream().map(Reservation::getId).toList()));

        // Los tipos del filtro: los de las actividades que reservó, por nombre
        Map<Integer, String> typeNames = new LinkedHashMap<>();
        all.stream().flatMap(r -> r.getInstance().getActivity().getTypes().stream())
                .sorted(Comparator.comparing(ActivityType::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(t -> typeNames.putIfAbsent(t.getId(), t.getName()));
        if (typeId != null && !typeNames.containsKey(typeId)) {
            typeId = null; // un tipo que no tiene ninguna de sus reservas: se ignora
        }
        final Integer type = typeId;

        List<Entry> matching = all.stream()
                .filter(r -> type == null || hasType(r.getInstance().getActivity(), type))
                .map(r -> entry(r, now, reviewed))
                .toList();

        // Paso 2: el calendario, de lunes a domingo
        LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        Map<LocalDate, List<Entry>> byDay = new LinkedHashMap<>();
        for (Entry e : matching) {
            byDay.computeIfAbsent(e.startAt().toLocalDate(), d -> new ArrayList<>()).add(e);
        }
        List<List<CalendarDay>> weeks = new ArrayList<>();
        List<CalendarDay> week = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            List<Entry> entries = byDay.getOrDefault(d, List.of());
            boolean inMonth = YearMonth.from(d).equals(month);
            String href = entries.isEmpty() ? null : href(YearMonth.from(d), d, type);
            week.add(new CalendarDay(d, inMonth, d.equals(today), d.equals(day), entries, href));
            if (week.size() == 7) {
                weeks.add(week);
                week = new ArrayList<>();
            }
        }

        // La lista: el día elegido o todo el mes
        final YearMonth shown = month;
        List<Entry> listed = matching.stream()
                .filter(e -> day != null ? e.startAt().toLocalDate().equals(day)
                        : YearMonth.from(e.startAt()).equals(shown))
                .toList();

        Entry next = matching.stream()
                .filter(e -> (e.status() == ReservationStatus.PENDING || e.status() == ReservationStatus.CONFIRMED)
                        && e.startAt().isAfter(now))
                .findFirst().orElse(null);
        String nextHref = next == null ? null : href(YearMonth.from(next.startAt()), next.startAt().toLocalDate(), type);

        List<TypeOption> types = typeNames.entrySet().stream().map(t -> new TypeOption(t.getKey(), t.getValue())).toList();
        return new MyReservations(!all.isEmpty(), month.toString(), DateLabels.month(month),
                href(month.minusMonths(1), null, type), href(month.plusMonths(1), null, type), href(month, null, type),
                type, types, day, weeks, listed, next, nextHref);
    }

    private static boolean hasType(Activity activity, Integer typeId) {
        return activity.getTypes().stream().anyMatch(t -> t.getId().equals(typeId));
    }

    private static Entry entry(Reservation r, LocalDateTime now, Set<Long> reviewed) {
        Activity activity = r.getInstance().getActivity();
        LocalDateTime start = r.getInstance().getStartAt();
        boolean payable = r.getStatus() == ReservationStatus.PENDING
                && ReservationStatusService.expiryReason(r, now) == null;
        boolean toReview = r.getStatus() == ReservationStatus.FINISHED && !reviewed.contains(r.getId());
        return new Entry(r.getId(), activity.getName(), activity.getBusiness().getName(), start,
                start.plusMinutes(activity.getDurationMinutes()), r.getParticipants(),
                Money.format(r.getTotalPrice()), r.getStatus(), payable, toReview);
    }

    /** "/tourist/reservations?month=2026-11&day=2026-11-01&type=3", sin los que no van. */
    static String href(YearMonth month, LocalDate day, Integer typeId) {
        StringJoiner query = new StringJoiner("&", "?", "");
        query.add("month=" + month);
        if (day != null) query.add("day=" + day);
        if (typeId != null) query.add("type=" + typeId);
        return PATH + query;
    }
}
