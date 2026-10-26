package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.MyReviews;
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

/** CU-24 Consultar mis valoraciones, contra H2: lo que le falta valorar y lo que ya valoró. */
@SpringBootTest
@Transactional
class MyReviewsPageIntegrationTest {

    private static final String URL = "/tourist/reviews";
    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private TouristRepository touristRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LocalityRepository localityRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityInstanceRepository instanceRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReviewRepository reviewRepository;

    private MockMvc mockMvc;
    private Provider provider;
    private Tourist tourist;
    private Business business;
    private Locality laPaloma;
    private Activity kayak;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        User providerUser = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        provider = providerRepository.save(Provider.builder().user(providerUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        business = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("x").address("x").phone("099").email("a@b.uy").build());
        tourist = tourist("ana@mail.com", "12345678");
        kayak = activity("Atardecer en kayak");
    }

    // ---------- Datos y sesiones de prueba ----------

    private Tourist tourist(String email, String nationalId) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        return touristRepository.save(Tourist.builder().user(user)
                .firstName("Ana").lastName("Pérez").nationalId(nationalId).build());
    }

    private Activity activity(String name) {
        return activityRepository.save(Activity.builder().business(business).locality(laPaloma).name(name)
                .description("x").durationMinutes(120).price(new BigDecimal("1500")).minAge(0)
                .bookingCutoffMinutes(60).active(true).build());
    }

    private Reservation reservation(Tourist t, Activity a, LocalDateTime start, ReservationStatus status) {
        ActivityInstance date = instanceRepository.save(ActivityInstance.builder().activity(a).startAt(start)
                .maxCapacity(10).build());
        return reservationRepository.save(Reservation.builder().tourist(t).instance(date).participants(2)
                .totalPrice(new BigDecimal("3000")).status(status).createdAt(start.minusDays(3)).build());
    }

    private void review(Reservation r, int rating, String comment, LocalDateTime when) {
        reviewRepository.save(Review.builder().reservation(r).rating(rating).comment(comment).createdAt(when).build());
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Ana");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private MyReviews model() throws Exception {
        return (MyReviews) mockMvc.perform(get(URL).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("reviews");
    }

    // ---------- Pasos 2 y 3 ----------

    @Test
    void showsWhatIsLeftToReviewAndWhatWasReviewed() throws Exception {
        Activity cerro = activity("Subida al cerro");
        Reservation toReview = reservation(tourist, cerro, NOW.minusDays(2), ReservationStatus.FINISHED);
        Reservation reviewed = reservation(tourist, kayak, NOW.minusDays(10), ReservationStatus.FINISHED);
        review(reviewed, 4, "Muy lindo paseo", NOW.minusDays(9));

        mockMvc.perform(get(URL).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("my-reviews"))
                .andExpect(content().string(containsString("Para valorar")))
                .andExpect(content().string(containsString("Subida al cerro")))
                // Paso 4: valora con el CU-18
                .andExpect(content().string(containsString("/tourist/reservations/" + toReview.getId() + "/review")))
                .andExpect(content().string(containsString("Ya valoradas")))
                .andExpect(content().string(containsString("★★★★☆")))
                .andExpect(content().string(containsString("Muy lindo paseo")))
                .andExpect(content().string(containsString("href=\"/activities/" + kayak.getId() + "\"")))
                .andExpect(content().string(not(containsString("/tourist/reservations/" + reviewed.getId() + "/review"))));
    }

    @Test
    void onlyFinishedReservationsAreOfferedForReview() throws Exception {
        reservation(tourist, kayak, NOW.plusDays(3), ReservationStatus.CONFIRMED);
        reservation(tourist, kayak, NOW.minusDays(3), ReservationStatus.EXPIRED);
        reservation(tourist, kayak, NOW.minusDays(4), ReservationStatus.CANCELLED);
        Reservation finished = reservation(tourist, kayak, NOW.minusDays(1), ReservationStatus.FINISHED);

        MyReviews page = model();

        assertThat(page.pending()).extracting(MyReviews.ToReview::reservationId).containsExactly(finished.getId());
        assertThat(page.done()).isEmpty();
    }

    @Test
    void reviews_areFromNewestToOldest() throws Exception {
        review(reservation(tourist, kayak, NOW.minusDays(20), ReservationStatus.FINISHED), 3, null, NOW.minusDays(19));
        review(reservation(tourist, kayak, NOW.minusDays(5), ReservationStatus.FINISHED), 5, "Otra vez", NOW.minusDays(4));

        assertThat(model().done()).extracting(d -> d.review().rating()).containsExactly(5, 3);
    }

    @Test
    void otherTouristsReviews_areNotShown() throws Exception {
        Tourist other = tourist("otro@mail.com", "87654321");
        review(reservation(other, kayak, NOW.minusDays(5), ReservationStatus.FINISHED), 1, "No me gustó", NOW);
        reservation(other, kayak, NOW.minusDays(2), ReservationStatus.FINISHED);

        MyReviews page = model();

        assertThat(page.pending()).isEmpty();
        assertThat(page.done()).isEmpty();
    }

    @Test
    void activityTakenDown_isShownWithoutLink() throws Exception {
        Reservation r = reservation(tourist, kayak, NOW.minusDays(5), ReservationStatus.FINISHED);
        review(r, 5, null, NOW.minusDays(4));
        kayak.setActive(false);

        mockMvc.perform(get(URL).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                .andExpect(content().string(not(containsString("href=\"/activities/" + kayak.getId() + "\""))));
    }

    // ---------- Alternativos ----------

    @Test
    void nothingToReviewYet_offersToExploreActivities() throws Exception {
        mockMvc.perform(get(URL).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no tenés actividades para valorar")))
                .andExpect(content().string(containsString("href=\"/activities\"")));
    }

    @Test
    void allReviewed_saysThereIsNothingPending() throws Exception {
        review(reservation(tourist, kayak, NOW.minusDays(5), ReservationStatus.FINISHED), 4, null, NOW);

        mockMvc.perform(get(URL).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("No tenés actividades pendientes de valorar")));
    }

    // ---------- Accesos ----------

    @Test
    void touristSpace_linksToMyReviews() throws Exception {
        mockMvc.perform(get("/tourist").with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("href=\"/tourist/reviews\"")));
    }

    @Test
    void provider_cannotSeeIt() throws Exception {
        mockMvc.perform(get(URL).with(as(Role.PROVIDER, provider.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutSession_isAskedToLogIn() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }
}
