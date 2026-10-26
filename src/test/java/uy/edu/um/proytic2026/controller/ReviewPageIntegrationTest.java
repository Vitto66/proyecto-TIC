package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;
import uy.edu.um.proytic2026.service.WeatherService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CU-18 Valorar y comentar una actividad, contra H2: valorar una reserva finalizada, 3a sin
 * puntuación, 1a una sola por reserva, y que se publique en el detalle con el promedio nuevo.
 */
@SpringBootTest
@Transactional
class ReviewPageIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);

    /** El detalle de la actividad pide el clima: acá no se sale a internet. */
    @MockitoBean private WeatherService weatherService;

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
    private Activity activity;
    private Reservation finished;

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
        tourist = tourist("ana@mail.com", "Ana", "Pérez", "12345678");
        activity = activityRepository.save(Activity.builder().business(business).locality(laPaloma)
                .name("Atardecer en kayak").description("Por la laguna").durationMinutes(150)
                .price(new BigDecimal("1500")).minAge(0).bookingCutoffMinutes(60).active(true).build());
        // Fue hace una semana: ya terminó y el CU-16 la pasó a FINALIZADA
        finished = reservation(tourist, NOW.minusDays(7), ReservationStatus.FINISHED);
    }

    // ---------- Datos y sesiones de prueba ----------

    private Tourist tourist(String email, String first, String last, String nationalId) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        return touristRepository.save(Tourist.builder().user(user)
                .firstName(first).lastName(last).nationalId(nationalId).build());
    }

    private Reservation reservation(Tourist t, LocalDateTime start, ReservationStatus status) {
        ActivityInstance date = instanceRepository.save(ActivityInstance.builder().activity(activity).startAt(start)
                .maxCapacity(10).build());
        return reservationRepository.save(Reservation.builder().tourist(t).instance(date).participants(2)
                .totalPrice(new BigDecimal("3000")).status(status).createdAt(start.minusDays(3)).build());
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Ana");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static String url(Reservation r) {
        return "/tourist/reservations/" + r.getId() + "/review";
    }

    private MockHttpServletRequestBuilder send(Tourist t, Reservation r, String rating, String comment) {
        MockHttpServletRequestBuilder req = post(url(r)).with(as(Role.TOURIST, t.getId())).with(csrf());
        if (rating != null) req.param("rating", rating);
        if (comment != null) req.param("comment", comment);
        return req;
    }

    // ---------- Pasos 1 a 4 ----------

    @Test
    void finishedReservation_offersToReviewIt() throws Exception {
        // Paso 1 y 2: desde la reserva finalizada elige "Valorar"
        mockMvc.perform(get("/tourist/reservations/" + finished.getId()).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"" + url(finished) + "\"")));
        // También desde "Mis reservas" (CU-17 4b)
        mockMvc.perform(get("/tourist/reservations?day=" + finished.getInstance().getStartAt().toLocalDate())
                        .with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("href=\"" + url(finished) + "\"")));

        mockMvc.perform(get(url(finished)).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("review"))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                .andExpect(content().string(containsString("Publicar valoración")));
    }

    @Test
    void review_isSavedAndPublishedInTheActivityDetail() throws Exception {
        mockMvc.perform(send(tourist, finished, "4", "  Muy lindo paseo, volvería.  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tourist/reservations/" + finished.getId() + "?reviewed"));

        Review saved = reviewRepository.findByReservationId(finished.getId()).orElseThrow();
        assertThat(saved.getRating()).isEqualTo(4);
        assertThat(saved.getComment()).isEqualTo("Muy lindo paseo, volvería.");
        assertThat(saved.getCreatedAt()).isNotNull();

        mockMvc.perform(get("/tourist/reservations/" + finished.getId()).param("reviewed", "")
                        .with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("¡Gracias por tu valoración!")))
                .andExpect(content().string(containsString("Tu valoración")))
                .andExpect(content().string(containsString("★★★★☆")));

        // Postcondición: se ve en el detalle público de la actividad, con nombre e inicial
        mockMvc.perform(get("/activities/" + activity.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Muy lindo paseo, volvería.")))
                .andExpect(content().string(containsString("Ana P.")));
    }

    @Test
    void averageOfTheActivity_isRecalculated() throws Exception {
        Tourist other = tourist("luis@mail.com", "Luis", "Gómez", "87654321");
        Reservation hers = reservation(other, NOW.minusDays(3), ReservationStatus.FINISHED);

        mockMvc.perform(send(tourist, finished, "5", null)).andExpect(status().is3xxRedirection());
        mockMvc.perform(send(other, hers, "2", "")).andExpect(status().is3xxRedirection());

        // (5 + 2) / 2 = 3,5
        mockMvc.perform(get("/activities/" + activity.getId()))
                .andExpect(content().string(containsString("3,5")))
                .andExpect(content().string(containsString("2 valoraciones")));
        // Un comentario vacío no se guarda como texto
        assertThat(reviewRepository.findByReservationId(hers.getId()).orElseThrow().getComment()).isNull();
    }

    // ---------- 3a: sin puntuación ----------

    @Test
    void withoutRating_isAskedToChooseOne() throws Exception {
        mockMvc.perform(send(tourist, finished, null, "Lindo"))
                .andExpect(status().isOk())
                .andExpect(view().name("review"))
                .andExpect(model().attributeHasFieldErrors("form", "rating"))
                .andExpect(content().string(containsString("elegí una puntuación de 1 a 5 estrellas")))
                // Lo que escribió no se pierde
                .andExpect(content().string(containsString("Lindo")));
        assertThat(reviewRepository.existsByReservationId(finished.getId())).isFalse();
    }

    @Test
    void ratingOutOfRange_isRejected() throws Exception {
        mockMvc.perform(send(tourist, finished, "6", null))
                .andExpect(model().attributeHasFieldErrors("form", "rating"));
        mockMvc.perform(send(tourist, finished, "0", null))
                .andExpect(model().attributeHasFieldErrors("form", "rating"));
        assertThat(reviewRepository.existsByReservationId(finished.getId())).isFalse();
    }

    @Test
    void commentTooLong_isRejected() throws Exception {
        mockMvc.perform(send(tourist, finished, "3", "a".repeat(1001)))
                .andExpect(model().attributeHasFieldErrors("form", "comment"));
        assertThat(reviewRepository.existsByReservationId(finished.getId())).isFalse();
    }

    // ---------- 1a: una sola por reserva ----------

    @Test
    void alreadyReviewed_isToldOnlyOneIsAllowed() throws Exception {
        mockMvc.perform(send(tourist, finished, "4", "Primera")).andExpect(status().is3xxRedirection());

        mockMvc.perform(get(url(finished)).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("Ya valoraste esta reserva")))
                .andExpect(content().string(containsString("Solo se permite una valoración por reserva")))
                .andExpect(content().string(not(containsString("Publicar valoración"))));

        // Si igual la envía de nuevo (por ejemplo, desde otra pestaña), no se guarda otra
        mockMvc.perform(send(tourist, finished, "1", "Segunda"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya valoraste esta reserva")));
        assertThat(reviewRepository.findByReservationId(finished.getId()).orElseThrow().getComment())
                .isEqualTo("Primera");
        assertThat(reviewRepository.count()).isEqualTo(1);
        // En "Mis reservas" ya no se ofrece valorarla
        mockMvc.perform(get("/tourist/reservations?day=" + finished.getInstance().getStartAt().toLocalDate())
                        .with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(not(containsString(url(finished)))));
    }

    // ---------- Precondición y permisos ----------

    @Test
    void reservationNotFinishedYet_cannotBeReviewed() throws Exception {
        Reservation upcoming = reservation(tourist, NOW.plusDays(5), ReservationStatus.CONFIRMED);

        mockMvc.perform(get(url(upcoming)).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no podés valorar esta reserva")))
                .andExpect(content().string(not(containsString("Publicar valoración"))));
        mockMvc.perform(send(tourist, upcoming, "5", null)).andExpect(status().isOk());
        assertThat(reviewRepository.existsByReservationId(upcoming.getId())).isFalse();
        // Y en su detalle no aparece "Valorar"
        mockMvc.perform(get("/tourist/reservations/" + upcoming.getId()).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(not(containsString(url(upcoming)))));
    }

    @Test
    void reservationOfAnotherTourist_isNotFound() throws Exception {
        Tourist other = tourist("luis@mail.com", "Luis", "Gómez", "87654321");

        mockMvc.perform(get(url(finished)).with(as(Role.TOURIST, other.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(send(other, finished, "1", "No fui")).andExpect(status().isNotFound());
        assertThat(reviewRepository.existsByReservationId(finished.getId())).isFalse();
    }

    @Test
    void withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post(url(finished)).with(as(Role.TOURIST, tourist.getId())).param("rating", "5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void provider_cannotReview() throws Exception {
        mockMvc.perform(get(url(finished)).with(as(Role.PROVIDER, provider.getId())))
                .andExpect(status().isForbidden());
    }
}
