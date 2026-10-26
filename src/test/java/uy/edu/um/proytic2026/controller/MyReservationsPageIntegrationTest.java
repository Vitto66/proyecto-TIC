package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.MyReservations;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CU-17 Consultar mis reservas, contra H2: el calendario del mes con cada reserva y su estado,
 * los filtros por fecha y por tipo, el detalle, y 4a / 4b (pagar o valorar).
 * Las fechas son de marzo de 2027 (lunes 1 a miércoles 31) para no depender del día de hoy.
 */
@SpringBootTest
@Transactional
class MyReservationsPageIntegrationTest {

    private static final String URL = "/tourist/reservations";
    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private TouristRepository touristRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LocalityRepository localityRepository;
    @Autowired private ActivityTypeRepository activityTypeRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityInstanceRepository instanceRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReviewRepository reviewRepository;

    private MockMvc mockMvc;
    private Provider provider;
    private Tourist tourist;
    private ActivityType nautica;
    private ActivityType trekking;
    private Activity kayak;
    private Activity cerro;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Locality laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        User providerUser = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        provider = providerRepository.save(Provider.builder().user(providerUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        Business business = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("x").address("x").phone("099").email("a@b.uy").build());
        tourist = tourist("ana@mail.com", "12345678");

        nautica = activityTypeRepository.save(ActivityType.builder().name("Náutica").build());
        trekking = activityTypeRepository.save(ActivityType.builder().name("Trekking").build());
        kayak = activity(business, laPaloma, "Atardecer en kayak", 150, nautica);
        cerro = activity(business, laPaloma, "Subida al cerro", 120, trekking);
    }

    // ---------- Datos y sesiones de prueba ----------

    private Tourist tourist(String email, String nationalId) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        return touristRepository.save(Tourist.builder().user(user)
                .firstName("Ana").lastName("Pérez").nationalId(nationalId).build());
    }

    private Activity activity(Business business, Locality locality, String name, int minutes, ActivityType type) {
        Activity a = Activity.builder().business(business).locality(locality).name(name).description("x")
                .durationMinutes(minutes).price(new BigDecimal("1500")).minAge(0).bookingCutoffMinutes(60)
                .active(true).build();
        a.getTypes().add(type);
        return activityRepository.save(a);
    }

    private Reservation reservation(Tourist t, Activity a, LocalDateTime start, ReservationStatus status,
                                    LocalDateTime createdAt) {
        ActivityInstance date = instanceRepository.save(ActivityInstance.builder().activity(a).startAt(start)
                .maxCapacity(10).build());
        return reservationRepository.save(Reservation.builder().tourist(t).instance(date).participants(2)
                .totalPrice(new BigDecimal("3000")).status(status).createdAt(createdAt).build());
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Ana");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private MockHttpServletRequestBuilder page(String query) {
        return get(URL + query).with(as(Role.TOURIST, tourist.getId()));
    }

    private MyReservations model(String query) throws Exception {
        return (MyReservations) mockMvc.perform(page(query)).andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("page");
    }

    // ---------- 2a: sin reservas ----------

    @Test
    void withoutReservations_offersToExploreActivities() throws Exception {
        mockMvc.perform(page(""))
                .andExpect(status().isOk())
                .andExpect(view().name("my-reservations"))
                .andExpect(content().string(containsString("Todavía no tenés reservas")))
                .andExpect(content().string(containsString("href=\"/activities\"")));
    }

    // ---------- Paso 2: el calendario con el estado de cada reserva ----------

    @Test
    void calendar_showsEachReservationWithItsStatus() throws Exception {
        Reservation k = reservation(tourist, kayak, LocalDateTime.of(2027, 3, 7, 10, 0), ReservationStatus.CONFIRMED, NOW);
        Reservation c = reservation(tourist, cerro, LocalDateTime.of(2027, 3, 20, 9, 0), ReservationStatus.PENDING, NOW);

        mockMvc.perform(page("?month=2027-03"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marzo de 2027")))
                .andExpect(content().string(containsString("10:00 Atardecer en kayak")))
                .andExpect(content().string(containsString("Confirmada")))
                .andExpect(content().string(containsString("09:00 Subida al cerro")))
                .andExpect(content().string(containsString("Pendiente de pago")))
                // Cada día con reservas se puede elegir, y cada reserva lleva a su detalle (paso 4)
                .andExpect(content().string(containsString("day=2027-03-07")))
                .andExpect(content().string(containsString("href=\"/tourist/reservations/" + k.getId() + "\"")))
                .andExpect(content().string(containsString("href=\"/tourist/reservations/" + c.getId() + "\"")));

        MyReservations p = model("?month=2027-03");
        assertThat(p.entries()).extracting(MyReservations.Entry::activityName)
                .containsExactly("Atardecer en kayak", "Subida al cerro");
        // Marzo de 2027 arranca un lunes y termina un miércoles: 5 semanas de lunes a domingo
        assertThat(p.weeks()).hasSize(5).allSatisfy(w -> assertThat(w).hasSize(7));
        assertThat(p.weeks().get(0).get(0).date()).isEqualTo(java.time.LocalDate.of(2027, 3, 1));
    }

    @Test
    void anotherMonth_listsNothingButOffersTheNextReservation() throws Exception {
        reservation(tourist, kayak, LocalDateTime.of(2027, 3, 7, 10, 0), ReservationStatus.CONFIRMED, NOW);

        MyReservations p = model("?month=2027-04");

        assertThat(p.entries()).isEmpty();
        assertThat(p.next().activityName()).isEqualTo("Atardecer en kayak");
        assertThat(p.nextHref()).isEqualTo("/tourist/reservations?month=2027-03&day=2027-03-07");
        mockMvc.perform(page("?month=2027-04"))
                .andExpect(content().string(containsString("No tenés reservas en estas fechas")))
                .andExpect(content().string(containsString("Tu próxima reserva")));
    }

    @Test
    void otherTouristsReservations_areNotShown() throws Exception {
        Tourist other = tourist("otro@mail.com", "87654321");
        reservation(other, kayak, LocalDateTime.of(2027, 3, 7, 10, 0), ReservationStatus.CONFIRMED, NOW);

        mockMvc.perform(page("?month=2027-03"))
                .andExpect(content().string(containsString("Todavía no tenés reservas")))
                .andExpect(content().string(not(containsString("Atardecer en kayak"))));
    }

    // ---------- Paso 3: filtros ----------

    @Test
    void filterByDay_showsOnlyThatDay() throws Exception {
        reservation(tourist, kayak, LocalDateTime.of(2027, 3, 7, 10, 0), ReservationStatus.CONFIRMED, NOW);
        reservation(tourist, cerro, LocalDateTime.of(2027, 3, 20, 9, 0), ReservationStatus.CONFIRMED, NOW);

        MyReservations p = model("?day=2027-03-07");

        assertThat(p.monthParam()).isEqualTo("2027-03");
        assertThat(p.entries()).extracting(MyReservations.Entry::activityName).containsExactly("Atardecer en kayak");
        assertThat(p.listTitle()).isEqualTo("Reservas del domingo 7 de marzo de 2027");
        mockMvc.perform(page("?day=2027-03-07")).andExpect(content().string(containsString("Ver todo el mes")));
    }

    @Test
    void filterByActivityType_showsOnlyThatType() throws Exception {
        reservation(tourist, kayak, LocalDateTime.of(2027, 3, 7, 10, 0), ReservationStatus.CONFIRMED, NOW);
        reservation(tourist, cerro, LocalDateTime.of(2027, 3, 20, 9, 0), ReservationStatus.CONFIRMED, NOW);

        MyReservations p = model("?month=2027-03&type=" + trekking.getId());

        assertThat(p.entries()).extracting(MyReservations.Entry::activityName).containsExactly("Subida al cerro");
        // El filtro ofrece los tipos de lo que reservó, por nombre
        assertThat(p.types()).extracting(MyReservations.TypeOption::name).containsExactly("Náutica", "Trekking");
        // El calendario también se filtra
        assertThat(p.weeks().stream().flatMap(List::stream).filter(MyReservations.CalendarDay::hasEntries).count())
                .isEqualTo(1);
    }

    @Test
    void parametersThatDoNotMakeSense_areIgnored() throws Exception {
        reservation(tourist, kayak, LocalDateTime.of(2027, 3, 7, 10, 0), ReservationStatus.CONFIRMED, NOW);

        mockMvc.perform(page("?month=marzo&day=ayer&type=x")).andExpect(status().isOk());
        // Un tipo que no está en ninguna de sus reservas: se muestran todas
        assertThat(model("?month=2027-03&type=999999").typeId()).isNull();
    }

    // ---------- 4a y 4b ----------

    @Test
    void pendingThatCanStillBePaid_offersToPay() throws Exception {
        Reservation payable = reservation(tourist, kayak, LocalDateTime.of(2027, 3, 7, 10, 0), ReservationStatus.PENDING, NOW);
        // Pendiente, pero creada hace 2 días: ya no se puede pagar (la vence el CU-16)
        Reservation late = reservation(tourist, cerro, LocalDateTime.of(2027, 3, 20, 9, 0), ReservationStatus.PENDING,
                NOW.minusDays(2));

        mockMvc.perform(page("?month=2027-03"))
                .andExpect(content().string(containsString("/tourist/reservations/" + payable.getId() + "/pay")))
                .andExpect(content().string(not(containsString("/tourist/reservations/" + late.getId() + "/pay"))));
    }

    @Test
    void finishedAndNotReviewed_offersToReview() throws Exception {
        LocalDateTime past = NOW.minusDays(10).toLocalDate().atTime(9, 0);
        Reservation reviewed = reservation(tourist, kayak, past, ReservationStatus.FINISHED, past.minusDays(3));
        reservation(tourist, cerro, past.plusHours(4), ReservationStatus.FINISHED, past.minusDays(3));
        reviewRepository.save(Review.builder().reservation(reviewed).rating(5).createdAt(NOW).build());

        MyReservations p = model("?day=" + past.toLocalDate());

        assertThat(p.entries()).extracting(MyReservations.Entry::activityName, MyReservations.Entry::toReview)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Atardecer en kayak", false),
                        org.assertj.core.groups.Tuple.tuple("Subida al cerro", true));
        mockMvc.perform(page("?day=" + past.toLocalDate()))
                .andExpect(content().string(containsString("Finalizada")))
                .andExpect(content().string(containsString("Valorar")));
    }

    @Test
    void expiredAndCancelled_areShownWithTheirStatus() throws Exception {
        reservation(tourist, kayak, LocalDateTime.of(2027, 3, 7, 10, 0), ReservationStatus.EXPIRED, NOW);
        reservation(tourist, cerro, LocalDateTime.of(2027, 3, 20, 9, 0), ReservationStatus.CANCELLED, NOW);

        mockMvc.perform(page("?month=2027-03"))
                .andExpect(content().string(containsString("Vencida")))
                .andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(containsString("is-expired")))
                .andExpect(content().string(containsString("is-cancelled")));
        // Ni vencidas ni canceladas cuentan como "próxima reserva"
        assertThat(model("?month=2027-03").next()).isNull();
    }

    // ---------- Accesos ----------

    @Test
    void touristSpace_linksToMyReservations() throws Exception {
        mockMvc.perform(get("/tourist").with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("href=\"/tourist/reservations\"")));
    }

    @Test
    void withoutSession_isAskedToLogIn() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void provider_cannotSeeIt() throws Exception {
        mockMvc.perform(get(URL).with(as(Role.PROVIDER, provider.getId())))
                .andExpect(status().isForbidden());
    }
}
