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
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-11 de punta a punta, contra H2 y con Spring Security activo. */
@SpringBootTest
@Transactional
class ProviderReservationsIntegrationTest {

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

    private MockMvc mockMvc;
    private Provider provider;
    private Locality laPaloma;
    private Activity activity;
    private ActivityInstance upcoming;
    private ActivityInstance past;
    private int document = 40000000;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        provider = approvedProviderWithBusiness("juan@mail.com", "Kayak Laguna");

        activity = activityRepository.save(Activity.builder().business(businessRepository.findAll().get(0))
                .locality(laPaloma).name("Atardecer en kayak").description("x").durationMinutes(150)
                .price(new BigDecimal("1500")).minAge(0).bookingCutoffMinutes(0).active(true).build());
        upcoming = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.plusDays(5).withHour(18).withMinute(0)).maxCapacity(10).build());
        past = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.minusDays(10).withHour(18).withMinute(0)).maxCapacity(null).build());
    }

    private Provider approvedProviderWithBusiness(String email, String businessName) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        Provider p = providerRepository.save(Provider.builder().user(user).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R")
                .document(String.valueOf(document++)).address("x").build());
        businessRepository.save(Business.builder().provider(p).locality(laPaloma).name(businessName)
                .description("x").address("x").phone("099").email("a@b.uy").build());
        return p;
    }

    private Tourist tourist(String first, String last, String email, String nationalId) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").phone("099 111 222").build());
        return touristRepository.save(Tourist.builder().user(user).firstName(first).lastName(last)
                .nationalId(nationalId).build());
    }

    private Reservation reservation(Tourist t, ActivityInstance i, int people, ReservationStatus status) {
        Reservation r = Reservation.builder().tourist(t).instance(i).participants(people)
                .totalPrice(new BigDecimal(1500 * people)).status(status).createdAt(NOW.minusDays(1)).build();
        for (int n = 2; n <= people; n++) {
            r.addCompanion(ReservationParticipant.builder().firstName("Acompañante" + n).lastName("Pérez")
                    .nationalId("5555555" + n).phone("098 000 00" + n).build());
        }
        return reservationRepository.save(r);
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Juan");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private RequestPostProcessor providerSession() {
        return as(Role.PROVIDER, provider.getId());
    }

    // ---------- Paso 2: las fechas con sus cupos ----------

    @Test
    void list_showsDatesWithOccupiedAndFreeSeatsAndReservations() throws Exception {
        reservation(tourist("Ana", "Pérez", "ana@mail.com", "12345678"), upcoming, 3, ReservationStatus.CONFIRMED);
        reservation(tourist("Luis", "Gómez", "luis@mail.com", "23456789"), upcoming, 2, ReservationStatus.PENDING);

        mockMvc.perform(get("/provider/reservations").with(providerSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider-reservations"))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                // solo la confirmada ocupa lugar: 10 - 3 = 7
                .andExpect(content().string(containsString("7 lugares libres de 10")))
                .andExpect(content().string(containsString("2 reservas")))
                .andExpect(content().string(containsString("Fechas pasadas (1)")))
                .andExpect(content().string(containsString("Recibiste 2 reservas")));
    }

    @Test
    void list_withoutReservations_saysSo() throws Exception {
        mockMvc.perform(get("/provider/reservations").with(providerSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ninguna de tus fechas tiene reservas todavía")));
    }

    @Test
    void list_withoutDates_explainsHowToAddThem() throws Exception {
        Provider other = approvedProviderWithBusiness("otro@mail.com", "Otro emprendimiento");

        mockMvc.perform(get("/provider/reservations").with(as(Role.PROVIDER, other.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no tenés fechas publicadas")));
    }

    @Test
    void list_providerWithoutBusiness_isAskedToRegisterIt() throws Exception {
        User user = userRepository.save(User.builder().email("sin@mail.com").passwordHash("x").build());
        Provider noBusiness = providerRepository.save(Provider.builder().user(user).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Sin").lastName("Emprendimiento")
                .document("39999999").address("x").build());

        mockMvc.perform(get("/provider/reservations").with(as(Role.PROVIDER, noBusiness.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Primero registrá tu emprendimiento")));
    }

    // ---------- Pasos 3 y 4: los turistas de una fecha ----------

    @Test
    void detail_showsTouristsPeopleAndStatus() throws Exception {
        reservation(tourist("Ana", "Pérez", "ana@mail.com", "12345678"), upcoming, 3, ReservationStatus.CONFIRMED);
        reservation(tourist("Luis", "Gómez", "luis@mail.com", "23456789"), upcoming, 1, ReservationStatus.PENDING);
        reservation(tourist("Eva", "Silva", "eva@mail.com", "34567890"), upcoming, 2, ReservationStatus.EXPIRED);

        mockMvc.perform(get("/provider/reservations/{id}", upcoming.getId()).with(providerSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("provider-reservation-detail"))
                .andExpect(content().string(containsString("Ana Pérez")))
                .andExpect(content().string(containsString("ana@mail.com")))
                .andExpect(content().string(containsString("3 personas")))
                .andExpect(content().string(containsString("Confirmada")))
                .andExpect(content().string(containsString("Pendiente de pago")))
                .andExpect(content().string(containsString("Vencida")))
                .andExpect(content().string(containsString("Acompañante2 Pérez")))
                .andExpect(content().string(containsString("3 personas confirmadas")))
                .andExpect(content().string(containsString("7 lugares libres de 10")));
    }

    @Test
    void detail_withoutReservations_saysSo() throws Exception {
        mockMvc.perform(get("/provider/reservations/{id}", past.getId()).with(providerSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Esta fecha todavía no tiene reservas")));
    }

    @Test
    void detail_ofAnotherProvidersDate_returns404() throws Exception {
        Provider other = approvedProviderWithBusiness("otro@mail.com", "Otro emprendimiento");

        mockMvc.perform(get("/provider/reservations/{id}", upcoming.getId()).with(as(Role.PROVIDER, other.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void touristsCannotSeeProviderReservations() throws Exception {
        mockMvc.perform(get("/provider/reservations").with(as(Role.TOURIST, 99L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerSpace_linksToReservations() throws Exception {
        mockMvc.perform(get("/provider").with(providerSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/provider/reservations")))
                .andExpect(content().string(containsString("/provider/proposals")));
    }

    @Test
    void list_onlyShowsOwnActivities() throws Exception {
        Provider other = approvedProviderWithBusiness("otro@mail.com", "Otro emprendimiento");
        Business otherBusiness = businessRepository.findAll().stream()
                .filter(b -> b.getProvider().getId().equals(other.getId())).findFirst().orElseThrow();
        Activity otherActivity = activityRepository.save(Activity.builder().business(otherBusiness)
                .locality(laPaloma).name("Cabalgata ajena").description("x").durationMinutes(60)
                .price(new BigDecimal("900")).minAge(0).bookingCutoffMinutes(0).active(true).build());
        instanceRepository.save(ActivityInstance.builder().activity(otherActivity)
                .startAt(NOW.plusDays(3)).maxCapacity(5).build());

        mockMvc.perform(get("/provider/reservations").with(providerSession()))
                .andExpect(content().string(not(containsString("Cabalgata ajena"))));
    }
}
