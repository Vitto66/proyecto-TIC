package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.HomeActivity;
import uy.edu.um.proytic2026.dto.HomeOverview;
import uy.edu.um.proytic2026.dto.HomeOverview.MapDepartment;
import uy.edu.um.proytic2026.dto.HomeOverview.TypeCard;
import uy.edu.um.proytic2026.dto.UpcomingActivity;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Datos del inicio: en qué departamentos hay actividades publicadas (para el mapa), las mejores
 * de cada uno (para el panel al lado del mapa), los tipos de actividad (para los botones y el
 * buscador), las más reservadas del mes y las que tienen las fechas más próximas. Cada etiqueta del mapa y cada tarjeta
 * llevan a la vidriera (CU-05) con el filtro correspondiente ya aplicado.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    /** Cuántas tarjetas de tipos se muestran. */
    static final int MAX_TYPE_CARDS = 6;

    private final ActivityRepository activityRepository;
    private final DepartmentRepository departmentRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final UruguayMap uruguayMap;
    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final ActivityInstanceRepository instanceRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HomeOverview overview() {
        List<Activity> published = activityRepository.findAllPublished();

        Map<String, Long> byDepartment = published.stream().collect(Collectors.groupingBy(
                a -> UruguayMap.normalize(a.getLocality().getDepartment().getName()), Collectors.counting()));
        Map<String, Integer> departmentIds = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(d -> UruguayMap.normalize(d.getName()), Department::getId, (a, b) -> a));

        List<MapDepartment> departments = uruguayMap.shapes().stream()
                .map(s -> new MapDepartment(s.iso(), s.name(), s.path(), departmentIds.get(s.key()),
                        s.labelX(), s.labelY(), uruguayMap.width(), uruguayMap.height(),
                        byDepartment.getOrDefault(s.key(), 0L)))
                .toList();

        // Cada actividad con su valoración y sus reservas del mes
        Map<Long, ReviewRepository.RatingStats> ratings = reviewRepository.ratingStats().stream()
                .collect(Collectors.toMap(ReviewRepository.RatingStats::getActivityId, r -> r));
        LocalDateTime monthStart = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();
        Map<Long, Long> bookings = reservationRepository.countBookingsSince(monthStart, ActivityDateService.SEAT_TAKING)
                .stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, HomeActivity> cards = new HashMap<>();
        for (Activity a : published) {
            cards.put(a.getId(), toCard(a, ratings.get(a.getId()), bookings.getOrDefault(a.getId(), 0L)));
        }

        // Panel del mapa: las mejores de cada departamento
        Map<String, String> isoByKey = uruguayMap.shapes().stream()
                .collect(Collectors.toMap(UruguayMap.Shape::key, UruguayMap.Shape::iso, (x, y) -> x));
        Map<String, List<HomeActivity>> panels = new HashMap<>();
        published.stream()
                .collect(Collectors.groupingBy(a -> UruguayMap.normalize(a.getLocality().getDepartment().getName())))
                .forEach((key, list) -> {
                    String iso = isoByKey.get(key);
                    if (iso != null) {
                        panels.put(iso, list.stream().map(a -> cards.get(a.getId()))
                                .sorted(BEST_RATED).limit(PANEL_SIZE).toList());
                    }
                });

        // Lo más reservado este mes; si todavía no hay reservas, las mejor valoradas
        boolean byBookings = cards.values().stream().anyMatch(c -> c.monthBookings() > 0);
        List<HomeActivity> featured = cards.values().stream()
                .sorted(byBookings ? Comparator.comparingLong(HomeActivity::monthBookings).reversed().thenComparing(BEST_RATED)
                        : BEST_RATED)
                .limit(FEATURED_SIZE)
                .toList();

        List<TypeCard> allTypes = typeCards(published, Integer.MAX_VALUE).stream()
                .sorted(Comparator.comparing(TypeCard::name, String.CASE_INSENSITIVE_ORDER)).toList();

        return new HomeOverview(uruguayMap.viewBox(), uruguayMap.width(), uruguayMap.height(),
                departments, typeCards(published, MAX_TYPE_CARDS), published.size(), allTypes, panels, featured,
                byBookings, upcoming(published, cards));
    }

    /**
     * Lo más próximo: las actividades con la fecha más cercana que todavía tiene lugar y que se
     * puede reservar (no pasó el cierre de reservas). Una tarjeta por actividad, con su próxima fecha.
     */
    private List<UpcomingActivity> upcoming(List<Activity> published, Map<Long, HomeActivity> cards) {
        if (published.isEmpty()) return List.of();
        LocalDateTime now = LocalDateTime.now(clock);
        List<ActivityInstance> dates = instanceRepository.findUpcoming(
                published.stream().map(Activity::getId).toList(), now);
        if (dates.isEmpty()) return List.of();
        Map<Long, Long> occupied = reservationRepository.occupiedSeats(
                        dates.stream().map(ActivityInstance::getId).toList(), ActivityDateService.SEAT_TAKING).stream()
                .collect(Collectors.toMap(ReservationRepository.OccupiedSeats::getInstanceId,
                        ReservationRepository.OccupiedSeats::getSeats));

        List<UpcomingActivity> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ActivityInstance i : dates.stream().sorted(Comparator.comparing(ActivityInstance::getStartAt)).toList()) {
            Activity a = i.getActivity();
            if (seen.contains(a.getId()) || !cards.containsKey(a.getId())) continue;
            Long free = i.getMaxCapacity() == null ? null : i.getMaxCapacity() - occupied.getOrDefault(i.getId(), 0L);
            boolean full = free != null && free <= 0;
            boolean closed = !now.isBefore(i.getStartAt().minusMinutes(a.getBookingCutoffMinutes()));
            if (full || closed) continue;
            seen.add(a.getId());
            result.add(new UpcomingActivity(cards.get(a.getId()), i.getStartAt(), free, now.toLocalDate()));
            if (result.size() == UPCOMING_SIZE) break;
        }
        return result;
    }

    /** Cuántas actividades se muestran en el panel del departamento y en las destacadas. */
    static final int PANEL_SIZE = 3;
    static final int FEATURED_SIZE = 4;
    static final int UPCOMING_SIZE = 4;

    /** Mejor promedio primero; a igual promedio, la que tiene más valoraciones; sin valoraciones, al final. */
    private static final Comparator<HomeActivity> BEST_RATED = Comparator
            .comparing((HomeActivity c) -> c.averageRating() == null ? -1.0 : c.averageRating()).reversed()
            .thenComparing(Comparator.comparingLong(HomeActivity::reviewCount).reversed())
            .thenComparing(HomeActivity::name, String.CASE_INSENSITIVE_ORDER);

    private static HomeActivity toCard(Activity a, ReviewRepository.RatingStats rating, long monthBookings) {
        String type = a.getTypes().stream().map(ActivityType::getName)
                .min(String.CASE_INSENSITIVE_ORDER).orElse("Actividad");
        String locality = a.getLocality().getName();
        String department = a.getLocality().getDepartment().getName();
        String location = locality.equalsIgnoreCase(department) ? department : locality + ", " + department;
        return new HomeActivity(a.getId(), a.getName(), location, type, HomeOverview.sceneFor(type), a.getPrice(),
                a.getDurationMinutes(), rating == null ? null : rating.getAverage(),
                rating == null ? 0 : rating.getCount(), monthBookings);
    }

    /** Los tipos activos con más actividades primero; a igual cantidad, por nombre. */
    private List<TypeCard> typeCards(List<Activity> published, int max) {
        Map<Integer, Long> byType = new HashMap<>();
        for (Activity a : published) {
            for (ActivityType t : a.getTypes()) {
                byType.merge(t.getId(), 1L, Long::sum);
            }
        }
        List<ActivityType> types = activityTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .sorted(Comparator.comparing((ActivityType t) -> byType.getOrDefault(t.getId(), 0L)).reversed()
                        .thenComparing(ActivityType::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(max)
                .toList();
        // Cada tarjeta toma un color distinto de la paleta, en orden
        return IntStream.range(0, types.size())
                .mapToObj(i -> new TypeCard(types.get(i).getId(), types.get(i).getName(),
                        byType.getOrDefault(types.get(i).getId(), 0L), i % 6))
                .toList();
    }
}
