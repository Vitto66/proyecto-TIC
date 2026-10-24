package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.ActivitySearch;
import uy.edu.um.proytic2026.dto.CatalogCard;
import uy.edu.um.proytic2026.dto.CatalogResult;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del CU-05 Explorar actividades publicadas: el orden por calificación y cada filtro.
 * <p>
 * Las tres actividades de ejemplo:
 * <ul>
 *   <li>Kayak (Rocha, $ 1.500, 4,5 ★ con 2 valoraciones): el día DAY está completo; el siguiente, no.</li>
 *   <li>Cabalgata (Maldonado, $ 3.000, 5 ★ con 1 valoración): el día DAY tiene lugar.</li>
 *   <li>Cata de vinos (Montevideo, $ 800, sin valoraciones ni fechas).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // cada test usa solo parte de los datos de ejemplo
class CatalogServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 25);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityInstanceRepository instanceRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ActivityTypeRepository activityTypeRepository;

    @Mock
    private LocationService locationService;

    private CatalogService service;

    private final Department rocha = Department.builder().id(1).name("Rocha").build();
    private final Department maldonado = Department.builder().id(2).name("Maldonado").build();
    private final Department montevideo = Department.builder().id(3).name("Montevideo").build();
    private final Locality laPaloma = Locality.builder().id(10).department(rocha).name("La Paloma").build();
    private final Locality puntaDelEste = Locality.builder().id(20).department(maldonado).name("Punta del Este").build();
    private final Locality mvd = Locality.builder().id(30).department(montevideo).name("Montevideo").build();

    private final ActivityType acuaticas = ActivityType.builder().id(1).name("Actividades acuáticas").build();
    private final ActivityType cabalgatas = ActivityType.builder().id(2).name("Cabalgatas").build();
    private final ActivityType enoturismo = ActivityType.builder().id(3).name("Enoturismo").build();

    private Activity kayak;
    private Activity cabalgata;
    private Activity vino;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ClockConfig.URUGUAY).toInstant(), ClockConfig.URUGUAY);
        service = new CatalogService(activityRepository, instanceRepository, reservationRepository,
                reviewRepository, activityTypeRepository, locationService, clock);

        kayak = activity(1L, "Atardecer en kayak", "Recorrido por la laguna", "Kayak Laguna", laPaloma, "1500", acuaticas);
        cabalgata = activity(2L, "Cabalgata serrana", "Paseo a caballo por la sierra", "Estancia Sierra", puntaDelEste, "3000", cabalgatas);
        vino = activity(3L, "Cata de vinos", "Vinos de la zona", "Bodega Centro", mvd, "800", enoturismo);
        when(activityRepository.findAllPublished()).thenReturn(List.of(vino, kayak, cabalgata));

        when(reviewRepository.ratingStats()).thenReturn(List.of(rating(1L, 4.5, 2), rating(2L, 5.0, 1)));

        ActivityInstance kayakFull = instance(11L, kayak, DAY.atTime(10, 0), 10);
        ActivityInstance kayakNext = instance(12L, kayak, DAY.plusDays(1).atTime(10, 0), null);
        ActivityInstance cabalgataDay = instance(21L, cabalgata, DAY.atTime(9, 0), 5);
        when(instanceRepository.findUpcoming(anyCollection(), any()))
                .thenReturn(List.of(cabalgataDay, kayakFull, kayakNext));
        // La de kayak del día DAY está llena: 10 de 10
        when(reservationRepository.occupiedSeats(anyCollection(), anyCollection()))
                .thenReturn(List.of(seats(11L, 10L)));
    }

    // ---------- Armado de datos ----------

    private static Activity activity(Long id, String name, String description, String businessName,
                                     Locality locality, String price, ActivityType type) {
        Business business = Business.builder().id(id * 10).name(businessName).build();
        Set<ActivityType> types = new LinkedHashSet<>(List.of(type));
        return Activity.builder().id(id).name(name).description(description).business(business)
                .locality(locality).price(new BigDecimal(price)).durationMinutes(120).minAge(0)
                .bookingCutoffMinutes(0).active(true).types(types).build();
    }

    private static ActivityInstance instance(Long id, Activity activity, LocalDateTime start, Integer capacity) {
        return ActivityInstance.builder().id(id).activity(activity).startAt(start).maxCapacity(capacity).build();
    }

    private static ReviewRepository.RatingStats rating(Long activityId, double average, long count) {
        return new ReviewRepository.RatingStats() {
            public Long getActivityId() { return activityId; }
            public Double getAverage() { return average; }
            public Long getCount() { return count; }
        };
    }

    private static ReservationRepository.OccupiedSeats seats(Long instanceId, Long seats) {
        return new ReservationRepository.OccupiedSeats() {
            public Long getInstanceId() { return instanceId; }
            public Long getSeats() { return seats; }
        };
    }

    private List<String> names(ActivitySearch search) {
        return service.search(search).activities().stream().map(CatalogCard::name).toList();
    }

    // ---------- Paso 2 y 3a: sin filtros ----------

    @Test
    void noFilters_showsAllFromBestToWorstRated_unratedLast() {
        assertThat(names(new ActivitySearch()))
                .containsExactly("Cabalgata serrana", "Atardecer en kayak", "Cata de vinos");
    }

    @Test
    void card_hasSummaryAverageAndNextAvailableDate() {
        CatalogCard card = service.search(new ActivitySearch()).activities().stream()
                .filter(c -> c.id().equals(1L)).findFirst().orElseThrow();

        assertThat(card.businessName()).isEqualTo("Kayak Laguna");
        assertThat(card.location()).isEqualTo("La Paloma, Rocha");
        assertThat(card.ratingLabel()).isEqualTo("4,5");
        assertThat(card.reviewsLabel()).isEqualTo("2 valoraciones");
        // La del día DAY está llena: la próxima con lugar es la del día siguiente
        assertThat(card.nextDate()).isEqualTo(DAY.plusDays(1).atTime(LocalTime.of(10, 0)));
    }

    @Test
    void unratedActivity_saysSo() {
        CatalogCard card = service.search(new ActivitySearch()).activities().stream()
                .filter(c -> c.id().equals(3L)).findFirst().orElseThrow();

        assertThat(card.rated()).isFalse();
        assertThat(card.reviewsLabel()).isEqualTo("Sin valoraciones todavía");
        assertThat(card.nextDate()).isNull();
    }

    @Test
    void nothingPublished_isReportedAs2a() {
        when(activityRepository.findAllPublished()).thenReturn(List.of());

        CatalogResult result = service.search(new ActivitySearch());

        assertThat(result.anyPublished()).isFalse();
        assertThat(result.noneMatch()).isFalse();
    }

    @Test
    void noMatches_isReportedAs4a() {
        CatalogResult result = service.search(ActivitySearch.builder().keyword("paracaidismo").build());

        assertThat(result.anyPublished()).isTrue();
        assertThat(result.noneMatch()).isTrue();
    }

    // ---------- Paso 3: cada filtro ----------

    @Test
    void filterByDepartment() {
        assertThat(names(ActivitySearch.builder().departmentId(2).build())).containsExactly("Cabalgata serrana");
    }

    @Test
    void filterByLocality() {
        assertThat(names(ActivitySearch.builder().localityId(10).build())).containsExactly("Atardecer en kayak");
    }

    @Test
    void filterByActivityType() {
        assertThat(names(ActivitySearch.builder().typeId(3).build())).containsExactly("Cata de vinos");
    }

    @Test
    void filterByDate_onlyActivitiesWithRoomThatDay() {
        // El día DAY el kayak está completo: queda solo la cabalgata
        assertThat(names(ActivitySearch.builder().date(DAY).build())).containsExactly("Cabalgata serrana");
        assertThat(names(ActivitySearch.builder().date(DAY.plusDays(1)).build())).containsExactly("Atardecer en kayak");
        assertThat(names(ActivitySearch.builder().date(DAY.plusDays(5)).build())).isEmpty();
    }

    @Test
    void filterByPriceRange() {
        assertThat(names(ActivitySearch.builder().minPrice(new BigDecimal("1000")).maxPrice(new BigDecimal("2000")).build()))
                .containsExactly("Atardecer en kayak");
        assertThat(names(ActivitySearch.builder().maxPrice(new BigDecimal("1500")).build()))
                .containsExactly("Atardecer en kayak", "Cata de vinos");
    }

    @Test
    void filterByPriceRange_reversedRangeIsSwapped() {
        assertThat(names(ActivitySearch.builder().minPrice(new BigDecimal("2000")).maxPrice(new BigDecimal("1000")).build()))
                .containsExactly("Atardecer en kayak");
    }

    @Test
    void filterByMinimumRating_excludesUnrated() {
        assertThat(names(ActivitySearch.builder().minRating(5).build())).containsExactly("Cabalgata serrana");
        assertThat(names(ActivitySearch.builder().minRating(4).build()))
                .containsExactly("Cabalgata serrana", "Atardecer en kayak");
    }

    @Test
    void filterByKeyword_ignoresCaseAndAccents_andSearchesNameDescriptionAndBusiness() {
        assertThat(names(ActivitySearch.builder().keyword("KAYAK").build())).containsExactly("Atardecer en kayak");
        // En la descripción
        assertThat(names(ActivitySearch.builder().keyword("caballo").build())).containsExactly("Cabalgata serrana");
        // En el nombre del emprendimiento
        assertThat(names(ActivitySearch.builder().keyword("bodega").build())).containsExactly("Cata de vinos");
        // Sin importar tildes
        assertThat(names(ActivitySearch.builder().keyword("cáta").build())).containsExactly("Cata de vinos");
        // Varias palabras: tienen que estar todas, en cualquier orden
        assertThat(names(ActivitySearch.builder().keyword("  sierra   estancia ").build())).containsExactly("Cabalgata serrana");
        assertThat(names(ActivitySearch.builder().keyword("sierra laguna").build())).isEmpty();
    }

    @Test
    void filtersCombine() {
        assertThat(names(ActivitySearch.builder().departmentId(1).typeId(1).maxPrice(new BigDecimal("2000")).build()))
                .containsExactly("Atardecer en kayak");
        assertThat(names(ActivitySearch.builder().departmentId(1).typeId(2).build())).isEmpty();
    }

    @Test
    void emptySearch_isDetected() {
        assertThat(new ActivitySearch().isEmpty()).isTrue();
        assertThat(ActivitySearch.builder().keyword("   ").build().isEmpty()).isTrue();
        assertThat(ActivitySearch.builder().minRating(3).build().isEmpty()).isFalse();
    }

    @Test
    void normalize_removesAccentsAndCase() {
        assertThat(CatalogService.normalize("Cabalgáta EN Tacuarembó")).isEqualTo("cabalgata en tacuarembo");
    }
}
