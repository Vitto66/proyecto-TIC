package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CU-14 de punta a punta, contra H2 y con Spring Security activo: elegir fecha y participantes,
 * el resumen con el total, confirmar, cupos (4a), cierre de reservas (4b) y quién puede reservar
 * (1a: sin sesión vuelve a la reserva después de iniciarla; 1b: el prestador no puede).
 */
@SpringBootTest
@Transactional
class ReservationPageIntegrationTest {

    private static final String PASSWORD = "secreta123";
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
    @Autowired private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private Provider provider;
    private Tourist tourist;
    private Activity activity;
    private ActivityInstance date;
    private String reserveUrl;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Locality laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());

        User providerUser = userRepository.save(User.builder().email("juan@mail.com")
                .passwordHash(passwordEncoder.encode(PASSWORD)).build());
        provider = providerRepository.save(Provider.builder().user(providerUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        providerUser.setProvider(provider);
        Business business = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("x").address("x").phone("099").email("a@b.uy").build());

        User touristUser = userRepository.save(User.builder().email("ana@mail.com")
                .passwordHash(passwordEncoder.encode(PASSWORD)).build());
        tourist = touristRepository.save(Tourist.builder().user(touristUser)
                .firstName("Ana").lastName("Pérez").nationalId("12345678").build());
        touristUser.setTourist(tourist);

        // $ 1.500 por persona, 2 h 30 min, las reservas cierran 60 minutos antes
        activity = activityRepository.save(Activity.builder().business(business).locality(laPaloma)
                .name("Atardecer en kayak").description("Por la laguna").durationMinutes(150)
                .price(new BigDecimal("1500")).minAge(0).bookingCutoffMinutes(60).active(true).build());
        date = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.plusDays(5).withHour(10).withMinute(0)).maxCapacity(10).build());
        reserveUrl = "/tourist/activities/" + activity.getId() + "/reserve";
    }

    // ---------- Datos y sesiones de prueba ----------

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Ana");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private RequestPostProcessor touristSession() {
        return as(Role.TOURIST, tourist.getId());
    }

    private MockHttpServletRequestBuilder preview(ActivityInstance instance, int participants) {
        return post(reserveUrl).with(touristSession()).with(csrf())
                .param("instanceId", instance.getId().toString())
                .param("participants", String.valueOf(participants));
    }

    private MockHttpServletRequestBuilder confirm(ActivityInstance instance, int participants) {
        return post(reserveUrl + "/confirm").with(touristSession()).with(csrf())
                .param("instanceId", instance.getId().toString())
                .param("participants", String.valueOf(participants));
    }

    private void confirmedReservation(ActivityInstance instance, int participants) {
        reservationRepository.save(Reservation.builder().tourist(tourist).instance(instance)
                .participants(participants).totalPrice(new BigDecimal("1500")).status(ReservationStatus.CONFIRMED)
                .createdAt(NOW.minusDays(1)).build());
    }

    // ---------- Pasos 1 a 3: el formulario ----------

    @Test
    void form_listsTheDatesAndPreselectsTheChosenOne() throws Exception {
        mockMvc.perform(get(reserveUrl).param("instanceId", date.getId().toString()).with(touristSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("reserve"))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                .andExpect(content().string(containsString("10 lugares libres")))
                .andExpect(content().string(containsString("$ 1.500")))
                .andExpect(content().string(containsString("checked")));
    }

    @Test
    void detailPage_linksToTheReservation() throws Exception {
        mockMvc.perform(get("/activities/" + activity.getId()).with(touristSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(reserveUrl)))
                .andExpect(content().string(containsString(reserveUrl + "?instanceId=" + date.getId())));
    }

    // ---------- Pasos 4 y 5: resumen con el total ----------

    @Test
    void preview_showsTheSummaryWithTheTotalPrice() throws Exception {
        mockMvc.perform(preview(date, 3))
                .andExpect(status().isOk())
                .andExpect(view().name("reserve-summary"))
                .andExpect(content().string(containsString("3 personas")))
                .andExpect(content().string(containsString("$ 4.500")))
                .andExpect(content().string(containsString("10:00 a 12:30")))
                .andExpect(content().string(containsString("Confirmar reserva")));

        // Todavía no se creó nada
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void preview_withoutChoosingADate_marksIt() throws Exception {
        mockMvc.perform(post(reserveUrl).with(touristSession()).with(csrf()).param("participants", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("reserve"))
                .andExpect(model().attributeHasFieldErrors("form", "instanceId"));
    }

    @Test
    void preview_notEnoughSeats_saysHowManyAreLeft() throws Exception {
        // 4a. Cupo 10, 8 ocupados: quedan 2
        confirmedReservation(date, 8);

        mockMvc.perform(preview(date, 3))
                .andExpect(status().isOk())
                .andExpect(view().name("reserve"))
                .andExpect(model().attributeHasFieldErrors("form", "participants"))
                .andExpect(content().string(containsString("quedan 2 lugares")));
    }

    @Test
    void preview_bookingClosed_isRejected() throws Exception {
        // 4b. Empieza en 30 minutos y las reservas cierran 60 minutos antes
        ActivityInstance soon = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.plusMinutes(30)).build());

        mockMvc.perform(preview(soon, 1))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "instanceId"))
                .andExpect(content().string(containsString("ya no se admiten reservas")));
    }

    // ---------- Paso 6: confirmar ----------

    @Test
    void confirm_createsAPendingReservationAndShowsIt() throws Exception {
        MvcResult result = mockMvc.perform(confirm(date, 2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(startsWith("/tourist/reservations/")))
                .andReturn();

        Reservation saved = reservationRepository.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(saved.getParticipants()).isEqualTo(2);
        assertThat(saved.getTotalPrice()).isEqualByComparingTo("3000");
        assertThat(saved.getTourist().getId()).isEqualTo(tourist.getId());

        mockMvc.perform(get(result.getResponse().getRedirectedUrl()).with(touristSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("reservation"))
                .andExpect(content().string(containsString("quedó creada")))
                .andExpect(content().string(containsString("Pendiente de pago")))
                .andExpect(content().string(containsString("$ 3.000")));
    }

    @Test
    void pendingReservation_doesNotTakeSeatsYet() throws Exception {
        mockMvc.perform(confirm(date, 4)).andExpect(status().is3xxRedirection());

        // Postcondición: todavía no ocupa cupos (los descuenta el pago, CU-15)
        mockMvc.perform(get("/activities/" + activity.getId()))
                .andExpect(content().string(containsString("10 lugares libres")));
    }

    @Test
    void confirm_whenTheSeatsWereTakenMeanwhile_doesNotCreateIt() throws Exception {
        confirmedReservation(date, 10);

        mockMvc.perform(confirm(date, 1))
                .andExpect(status().isOk())
                .andExpect(view().name("reserve"))
                .andExpect(content().string(containsString("ya no tiene lugares")));
        assertThat(reservationRepository.findAll())
                .noneMatch(r -> r.getStatus() == ReservationStatus.PENDING);
    }

    @Test
    void confirm_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post(reserveUrl + "/confirm").with(touristSession())
                        .param("instanceId", date.getId().toString()).param("participants", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservationOfAnotherTourist_isNotFound() throws Exception {
        mockMvc.perform(confirm(date, 1)).andExpect(status().is3xxRedirection());
        Long id = reservationRepository.findAll().get(0).getId();

        User otherUser = userRepository.save(User.builder().email("otro@mail.com").passwordHash("x").build());
        Tourist other = touristRepository.save(Tourist.builder().user(otherUser)
                .firstName("Otro").lastName("T").nationalId("87654321").build());

        mockMvc.perform(get("/tourist/reservations/" + id).with(as(Role.TOURIST, other.getId())))
                .andExpect(status().isNotFound());
    }

    // ---------- 1a y 1b: quién puede reservar ----------

    @Test
    void withoutSession_isAskedToLogIn_andComesBackToTheReservation() throws Exception {
        String target = reserveUrl + "?instanceId=" + date.getId();
        MockHttpSession session = new MockHttpSession();

        // 1a. Sin sesión → se pide iniciarla
        mockMvc.perform(get(target).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));

        // Después de iniciar sesión como turista, vuelve a la reserva (y no a su espacio)
        mockMvc.perform(post("/login").session(session).with(csrf())
                        .param("email", "ana@mail.com").param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(startsWith(target)));
    }

    @Test
    void login_withoutAPendingPage_stillGoesToTheSpace() throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("email", "ana@mail.com").param("password", PASSWORD))
                .andExpect(redirectedUrl("/tourist"));
    }

    @Test
    void providerProfile_cannotReserve() throws Exception {
        // 1b. Ingresó como prestador → no puede reservar
        mockMvc.perform(get(reserveUrl).with(as(Role.PROVIDER, provider.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/activities/" + activity.getId()).with(as(Role.PROVIDER, provider.getId())))
                .andExpect(content().string(containsString("perfil de turista")));
    }

    @Test
    void inactiveActivity_cannotBeReserved() throws Exception {
        activity.setActive(false);

        mockMvc.perform(get(reserveUrl).with(touristSession())).andExpect(status().isNotFound());
    }
}
