package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ActivityFormOptions.Option;
import uy.edu.um.proytic2026.dto.ActivitySearch;
import uy.edu.um.proytic2026.dto.CatalogCalendar;
import uy.edu.um.proytic2026.dto.DateLabels;
import uy.edu.um.proytic2026.dto.HomeOverview;
import uy.edu.um.proytic2026.dto.CatalogCard;
import uy.edu.um.proytic2026.dto.CatalogFilters;
import uy.edu.um.proytic2026.dto.CatalogResult;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * CU-05 Explorar actividades publicadas: la vidriera pública.
 * <p>
 * Se cargan todas las actividades publicadas y se filtran en memoria. Para la cantidad de
 * actividades de este proyecto alcanza y deja las reglas fáciles de leer y de probar; si un día
 * fueran miles, convendría pasar los filtros a la consulta y paginar.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final Locale ES_UY = Locale.forLanguageTag("es-UY");

    private final ActivityRepository activityRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final LocationService locationService;
    private final Clock clock;

    /** Opciones de los filtros (paso 3). */
    @Transactional(readOnly = true)
    public CatalogFilters filters() {
        return new CatalogFilters(locationService.options(),
                activityTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                        .map(t -> new Option(t.getId(), t.getName())).toList());
    }

    /**
     * Pasos 2 a 4: las actividades publicadas que cumplen los filtros. Sin filtros (3a) o con
     * ellos, se ordenan de mejor a peor calificadas; las que todavía no tienen valoraciones van
     * al final.
     */
    @Transactional(readOnly = true)
    public CatalogResult search(ActivitySearch search) {
        List<Activity> published = activityRepository.findAllPublished();
        if (published.isEmpty()) {
            return new CatalogResult(List.of(), false); // 2a
        }

        Map<Long, ReviewRepository.RatingStats> ratings = reviewRepository.ratingStats().stream()
                .collect(Collectors.toMap(ReviewRepository.RatingStats::getActivityId, r -> r));
        Map<Long, List<ActivityInstance>> availableDates = availableDates(published);

        Predicate<Activity> matches = filter(search, ratings, availableDates);
        List<CatalogCard> cards = published.stream()
                .filter(matches)
                .map(a -> toCard(a, ratings.get(a.getId()), availableDates.getOrDefault(a.getId(), List.of())))
                .sorted(bestRatedFirst())
                .toList();
        return new CatalogResult(cards, true);
    }

    /**
     * Vista de calendario: las fechas disponibles (futuras y con lugar) de las actividades que
     * cumplen los filtros, en el mes pedido. Un mes que ya pasó se cambia por el actual.
     */
    @Transactional(readOnly = true)
    public CatalogCalendar calendar(ActivitySearch search, YearMonth requested) {
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        YearMonth month = requested == null || requested.isBefore(current) ? current : requested;

        List<Activity> published = activityRepository.findAllPublished();
        Map<Long, ReviewRepository.RatingStats> ratings = reviewRepository.ratingStats().stream()
                .collect(Collectors.toMap(ReviewRepository.RatingStats::getActivityId, r -> r));
        Map<Long, List<ActivityInstance>> availableDates = availableDates(published);
        Map<Long, Long> occupied = occupiedSeats(availableDates);
        Predicate<Activity> matches = filter(search, ratings, availableDates);

        // Las fechas del mes, agrupadas por día y ordenadas por hora
        Map<LocalDate, List<CatalogCalendar.Slot>> byDay = new HashMap<>();
        int total = 0;
        for (Activity a : published) {
            if (!matches.test(a)) continue;
            String scene = HomeOverview.sceneFor(a.getTypes().stream().map(ActivityType::getName).sorted()
                    .findFirst().orElse(""));
            for (ActivityInstance i : availableDates.getOrDefault(a.getId(), List.of())) {
                LocalDate day = i.getStartAt().toLocalDate();
                if (!YearMonth.from(day).equals(month)) continue;
                if (search.getDate() != null && !day.equals(search.getDate())) continue;
                Long free = i.getMaxCapacity() == null ? null : i.getMaxCapacity() - occupied.getOrDefault(i.getId(), 0L);
                byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(new CatalogCalendar.Slot(a.getId(), i.getId(),
                        a.getName(), DateLabels.time(i.getStartAt()), free, scene));
                total++;
            }
        }
        byDay.values().forEach(list -> list.sort(Comparator.comparing(CatalogCalendar.Slot::time)
                .thenComparing(CatalogCalendar.Slot::activityName)));

        LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<List<CatalogCalendar.Day>> weeks = new ArrayList<>();
        List<CatalogCalendar.Day> week = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            week.add(new CatalogCalendar.Day(d, YearMonth.from(d).equals(month), d.equals(today),
                    byDay.getOrDefault(d, List.of())));
            if (week.size() == 7) {
                weeks.add(week);
                week = new ArrayList<>();
            }
        }
        return CatalogCalendar.of(month, current, weeks, total);
    }

    /** Lugares ocupados de cada fecha (las reservas pagas). */
    private Map<Long, Long> occupiedSeats(Map<Long, List<ActivityInstance>> availableDates) {
        List<Long> ids = availableDates.values().stream().flatMap(List::stream).map(ActivityInstance::getId).toList();
        if (ids.isEmpty()) return Map.of();
        return reservationRepository.occupiedSeats(ids, ActivityDateService.SEAT_TAKING).stream()
                .collect(Collectors.toMap(ReservationRepository.OccupiedSeats::getInstanceId,
                        ReservationRepository.OccupiedSeats::getSeats));
    }

    /** Las fechas futuras de cada actividad que todavía tienen lugar, de la más cercana a la más lejana. */
    private Map<Long, List<ActivityInstance>> availableDates(List<Activity> activities) {
        List<Long> ids = activities.stream().map(Activity::getId).toList();
        List<ActivityInstance> upcoming = instanceRepository.findUpcoming(ids, LocalDateTime.now(clock));
        if (upcoming.isEmpty()) return Map.of();

        Map<Long, Long> occupied = reservationRepository.occupiedSeats(
                        upcoming.stream().map(ActivityInstance::getId).toList(), ActivityDateService.SEAT_TAKING).stream()
                .collect(Collectors.toMap(ReservationRepository.OccupiedSeats::getInstanceId,
                        ReservationRepository.OccupiedSeats::getSeats));

        return upcoming.stream()
                .filter(i -> i.getMaxCapacity() == null || occupied.getOrDefault(i.getId(), 0L) < i.getMaxCapacity())
                .collect(Collectors.groupingBy(i -> i.getActivity().getId()));
    }

    private Predicate<Activity> filter(ActivitySearch s, Map<Long, ReviewRepository.RatingStats> ratings,
                                       Map<Long, List<ActivityInstance>> availableDates) {
        Predicate<Activity> p = a -> true;

        if (s.getDepartmentId() != null) {
            p = p.and(a -> Objects.equals(a.getLocality().getDepartment().getId(), s.getDepartmentId()));
        }
        if (s.getLocalityId() != null) {
            p = p.and(a -> Objects.equals(a.getLocality().getId(), s.getLocalityId()));
        }
        if (s.getTypeId() != null) {
            p = p.and(a -> a.getTypes().stream().anyMatch(t -> Objects.equals(t.getId(), s.getTypeId())));
        }
        if (s.getDate() != null) {
            p = p.and(a -> availableDates.getOrDefault(a.getId(), List.of()).stream()
                    .anyMatch(i -> i.getStartAt().toLocalDate().equals(s.getDate())));
        }

        // Si ponen el rango al revés (mínimo mayor que máximo), se da vuelta
        BigDecimal min = s.getMinPrice();
        BigDecimal max = s.getMaxPrice();
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }
        if (min != null) {
            BigDecimal from = min;
            p = p.and(a -> a.getPrice().compareTo(from) >= 0);
        }
        if (max != null) {
            BigDecimal to = max;
            p = p.and(a -> a.getPrice().compareTo(to) <= 0);
        }

        if (s.getMinRating() != null) {
            p = p.and(a -> {
                ReviewRepository.RatingStats r = ratings.get(a.getId());
                return r != null && r.getAverage() >= s.getMinRating();
            });
        }

        if (s.getKeyword() != null && !s.getKeyword().isBlank()) {
            // Todas las palabras tienen que aparecer, en cualquier orden, sin importar mayúsculas ni tildes
            List<String> words = Arrays.stream(normalize(s.getKeyword()).split("\\s+")).filter(w -> !w.isEmpty()).toList();
            p = p.and(a -> {
                String text = normalize(a.getName() + " " + Objects.toString(a.getDescription(), "")
                        + " " + a.getBusiness().getName());
                return words.stream().allMatch(text::contains);
            });
        }
        return p;
    }

    /** Mejor calificadas primero; empate: más valoraciones; después, por nombre. Las sin valorar, al final. */
    private static Comparator<CatalogCard> bestRatedFirst() {
        Collator collator = Collator.getInstance(ES_UY);
        Comparator<CatalogCard> byRating = Comparator.comparing(CatalogCard::averageRating,
                Comparator.nullsLast(Comparator.<Double>reverseOrder()));
        Comparator<CatalogCard> byReviews = Comparator.comparing(CatalogCard::reviewCount, Comparator.<Long>reverseOrder());
        Comparator<CatalogCard> byName = Comparator.comparing(CatalogCard::name, collator);
        return byRating.thenComparing(byReviews).thenComparing(byName);
    }

    private static CatalogCard toCard(Activity a, ReviewRepository.RatingStats rating, List<ActivityInstance> dates) {
        return new CatalogCard(a.getId(), a.getName(), a.getBusiness().getName(),
                a.getLocality().getName(), a.getLocality().getDepartment().getName(),
                a.getTypes().stream().map(ActivityType::getName).sorted().toList(),
                a.getPrice(), a.getDurationMinutes(), a.getMinAge(),
                rating == null ? null : rating.getAverage(),
                rating == null ? 0 : rating.getCount(),
                dates.isEmpty() ? null : dates.get(0).getStartAt());
    }

    /** Minúsculas y sin tildes: "Cabalgáta" → "cabalgata". */
    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
