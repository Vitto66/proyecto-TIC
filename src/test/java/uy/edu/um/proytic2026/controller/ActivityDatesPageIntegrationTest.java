package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.entity.Role;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CU-09 de punta a punta, contra H2 y con Spring Security activo: ver las fechas de una
 * actividad, agregar una, cupos, fechas pasadas (3a), superposición (3b) y quién puede entrar.
 */
@SpringBootTest
@Transactional
class ActivityDatesPageIntegrationTest {

    /** Una fecha cómoda en el futuro, para que el test no dependa del día en que corre. */
    private static final LocalDate FUTURE_DAY = LocalDate.now(ClockConfig.URUGUAY).plusDays(30);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private TouristRepository touristRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LocalityRepository localityRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityInstanceRepository instanceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private Provider owner;
    private Activity activity;
    private String url;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Locality laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        owner = provider("juan@mail.com", "45678901");
        Business business = businessRepository.save(Business.builder().provider(owner).locality(laPaloma)
                .name("Kayak Laguna").description("Salidas").address("Ruta 10").phone("099").email("a@b.uy").build());
        // Dura 2 h 30 min
        activity = activityRepository.save(Activity.builder().business(business).locality(laPaloma)
                .name("Atardecer en kayak").description("Por la laguna").durationMinutes(150)
                .price(new BigDecimal("1500")).minAge(0).bookingCutoffMinutes(0).active(true).build());
        url = "/provider/activities/" + activity.getId() + "/dates";
    }

    // ---------- Datos y sesiones de prueba ----------

    private Provider provider(String email, String document) {
        User user = userRepository.save(User.builder()
                .email(email).passwordHash(passwordEncoder.encode("secreta123")).build());
        Provider provider = providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.PHYSICAL).status(ProviderStatus.APPROVED)
                .firstName("Juan").lastName("Rodríguez").document(document).address("Ruta 10 km 264")
                .build());
        user.setProvider(provider);
        return provider;
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Juan");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private RequestPostProcessor ownerSession() {
        return as(Role.PROVIDER, owner.getId());
    }

    private MockHttpServletRequestBuilder addDate(LocalDate date, String time, String capacity) {
        return addDate(ownerSession(), date, time, capacity);
    }

    private MockHttpServletRequestBuilder addDate(RequestPostProcessor session, LocalDate date, String time,
                                                  String capacity) {
        MockHttpServletRequestBuilder request = post(url).with(session).with(csrf())
                .param("date", date.toString())
                .param("time", time);
        return capacity == null ? request : request.param("maxCapacity", capacity);
    }

    private ActivityInstance existingDate(LocalTime time, Integer capacity) {
        return instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(LocalDateTime.of(FUTURE_DAY, time)).maxCapacity(capacity).build());
    }

    // ---------- Paso 1: ver las fechas ----------

    @Test
    void page_withoutDates_showsEmptyMessageAndForm() throws Exception {
        mockMvc.perform(get(url).with(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-dates"))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                .andExpect(content().string(containsString("Todavía no hay fechas próximas")))
                .andExpect(content().string(containsString("Agregar fecha")));
    }

    @Test
    void myActivitiesList_linksToTheDatesPage() throws Exception {
        mockMvc.perform(get("/provider/activities").with(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(url)));
    }

    // ---------- Pasos 2 a 4: agregar ----------

    @Test
    void addDate_valid_isSavedAndShown() throws Exception {
        mockMvc.perform(addDate(FUTURE_DAY, "10:00", "12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(url))
                .andExpect(flash().attributeExists("added"));

        List<ActivityInstance> saved = instanceRepository.findByActivityIdOrderByStartAtAsc(activity.getId());
        assertThat(saved).singleElement().satisfies(i -> {
            assertThat(i.getStartAt()).isEqualTo(LocalDateTime.of(FUTURE_DAY, LocalTime.of(10, 0)));
            assertThat(i.getMaxCapacity()).isEqualTo(12);
        });

        mockMvc.perform(get(url).with(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("10:00 a 12:30")))
                .andExpect(content().string(containsString("12 lugares libres de 12")));
    }

    @Test
    void addDate_withoutCapacity_hasNoLimit() throws Exception {
        // 2a. No indica cantidad máxima → sin límite
        mockMvc.perform(addDate(FUTURE_DAY, "10:00", null)).andExpect(status().is3xxRedirection());

        assertThat(instanceRepository.findByActivityIdOrderByStartAtAsc(activity.getId()))
                .singleElement().satisfies(i -> assertThat(i.getMaxCapacity()).isNull());
        mockMvc.perform(get(url).with(ownerSession()))
                .andExpect(content().string(containsString("Sin límite de cupos")));
    }

    @Test
    void addDate_emptyForm_marksDateAndTime() throws Exception {
        mockMvc.perform(post(url).with(ownerSession()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "date", "time"));
    }

    @Test
    void addDate_zeroCapacity_isRejected() throws Exception {
        mockMvc.perform(addDate(FUTURE_DAY, "10:00", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "maxCapacity"));
        assertThat(instanceRepository.count()).isZero();
    }

    @Test
    void addDate_pastDate_isRejectedAndExplained() throws Exception {
        // 3a. Fecha ya pasada
        mockMvc.perform(addDate(LocalDate.now(ClockConfig.URUGUAY).minusDays(1), "10:00", null))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "date"))
                .andExpect(content().string(containsString("tienen que ser futuras")));
        assertThat(instanceRepository.count()).isZero();
    }

    @Test
    void addDate_sameStartAsAnother_isRejected() throws Exception {
        existingDate(LocalTime.of(10, 0), null);

        mockMvc.perform(addDate(FUTURE_DAY, "10:00", null))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "time"));
        assertThat(instanceRepository.count()).isEqualTo(1);
    }

    // ---------- 3b. Superposición ----------

    @Test
    void addDate_overlapping_warnsAndAsksForConfirmation() throws Exception {
        existingDate(LocalTime.of(10, 0), null);

        // 11:00 se pisa con la de 10:00 a 12:30
        mockMvc.perform(addDate(FUTURE_DAY, "11:00", null))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-dates"))
                .andExpect(content().string(containsString("se superpone con otra")))
                .andExpect(content().string(containsString("agregarla igual")));
        assertThat(instanceRepository.count()).isEqualTo(1);

        // Confirma
        mockMvc.perform(addDate(FUTURE_DAY, "11:00", null).param("confirmOverlap", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(instanceRepository.count()).isEqualTo(2);
    }

    @Test
    void addDate_afterTheOtherEnds_doesNotAsk() throws Exception {
        existingDate(LocalTime.of(10, 0), null);

        mockMvc.perform(addDate(FUTURE_DAY, "12:30", null))
                .andExpect(status().is3xxRedirection());
        assertThat(instanceRepository.count()).isEqualTo(2);
    }

    // ---------- Cupos ocupados ----------

    @Test
    void page_onlyConfirmedReservationsTakeSeats() throws Exception {
        ActivityInstance date = existingDate(LocalTime.of(10, 0), 10);
        User user = userRepository.save(User.builder().email("ana@mail.com").passwordHash("x").build());
        Tourist tourist = touristRepository.save(Tourist.builder().user(user)
                .firstName("Ana").lastName("Pérez").nationalId("12345678").build());
        reservation(tourist, date, 3, ReservationStatus.CONFIRMED);
        // Una pendiente todavía no ocupa lugar (CU-14) y una vencida ya no (CU-16)
        reservation(tourist, date, 2, ReservationStatus.PENDING);
        reservation(tourist, date, 4, ReservationStatus.EXPIRED);

        mockMvc.perform(get(url).with(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("7 lugares libres de 10")));
    }

    private void reservation(Tourist tourist, ActivityInstance date, int participants, ReservationStatus status) {
        reservationRepository.save(Reservation.builder().tourist(tourist).instance(date)
                .participants(participants).totalPrice(new BigDecimal("1500").multiply(BigDecimal.valueOf(participants)))
                .status(status).createdAt(LocalDateTime.now(ClockConfig.URUGUAY)).build());
    }

    // ---------- Precondiciones y permisos ----------

    @Test
    void activityOfAnotherProvider_isNotFound() throws Exception {
        Provider other = provider("otro@mail.com", "11111111");

        mockMvc.perform(get(url).with(as(Role.PROVIDER, other.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(addDate(as(Role.PROVIDER, other.getId()), FUTURE_DAY, "10:00", null))
                .andExpect(status().isNotFound());
        assertThat(instanceRepository.count()).isZero();
    }

    @Test
    void inactiveActivity_showsNoticeAndNoForm() throws Exception {
        activity.setActive(false);

        mockMvc.perform(get(url).with(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("está dada de baja")))
                .andExpect(content().string(not(containsString("Agregar fecha"))));

        mockMvc.perform(addDate(FUTURE_DAY, "10:00", null))
                .andExpect(status().isOk());
        assertThat(instanceRepository.count()).isZero();
    }

    @Test
    void withoutSession_redirectsToLogin_andOtherRolesAreForbidden() throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
        mockMvc.perform(get(url).with(as(Role.TOURIST, 1L))).andExpect(status().isForbidden());
    }

    @Test
    void addDate_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post(url).with(ownerSession()).param("date", FUTURE_DAY.toString()).param("time", "10:00"))
                .andExpect(status().isForbidden());
    }
}
