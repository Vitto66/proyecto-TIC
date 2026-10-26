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
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;

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

/** CU-16 paso 4: el turista ve en su espacio los avisos de reservas vencidas y los marca como vistos. */
@SpringBootTest
@Transactional
class NotificationPageIntegrationTest {

    private static final String MESSAGE = "Tu reserva de «Atardecer en kayak» para el domingo 1 de noviembre "
            + "de 2026 a las 10:00 venció porque pasaron 24 horas sin que se aprobara el pago. No se te cobró nada.";

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
    @Autowired private TouristNotificationRepository notificationRepository;

    private MockMvc mockMvc;
    private Tourist tourist;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Locality laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        User providerUser = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder().user(providerUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        Business business = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("x").address("x").phone("099").email("a@b.uy").build());
        Activity activity = activityRepository.save(Activity.builder().business(business).locality(laPaloma)
                .name("Atardecer en kayak").description("x").durationMinutes(150)
                .price(new BigDecimal("1500")).minAge(0).bookingCutoffMinutes(60).active(true).build());
        ActivityInstance date = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(LocalDateTime.of(2026, 11, 1, 10, 0)).maxCapacity(10).build());

        tourist = tourist("ana@mail.com", "12345678");
        reservation = reservationRepository.save(Reservation.builder().tourist(tourist).instance(date).participants(2)
                .totalPrice(new BigDecimal("3000")).status(ReservationStatus.EXPIRED)
                .createdAt(LocalDateTime.of(2026, 10, 25, 10, 0)).build());
    }

    private Tourist tourist(String email, String nationalId) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        return touristRepository.save(Tourist.builder().user(user)
                .firstName("Ana").lastName("Pérez").nationalId(nationalId).build());
    }

    private void notify(Tourist t, Reservation r) {
        notificationRepository.save(TouristNotification.builder().tourist(t).reservation(r).message(MESSAGE)
                .createdAt(LocalDateTime.of(2026, 10, 26, 10, 0)).build());
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Ana");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @Test
    void touristSpace_showsTheNoticeWithALinkToTheReservation() throws Exception {
        notify(tourist, reservation);

        mockMvc.perform(get("/tourist").with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tenés un aviso")))
                .andExpect(content().string(containsString("venció porque pasaron 24 horas")))
                .andExpect(content().string(containsString("/tourist/reservations/" + reservation.getId())))
                .andExpect(content().string(containsString("Lun 26 oct, 10:00")));
    }

    @Test
    void withoutNotices_nothingIsShown() throws Exception {
        mockMvc.perform(get("/tourist").with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Marcar como vistos"))));
    }

    @Test
    void anotherTourist_doesNotSeeIt() throws Exception {
        notify(tourist, reservation);
        Tourist other = tourist("otro@mail.com", "87654321");

        mockMvc.perform(get("/tourist").with(as(Role.TOURIST, other.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("venció porque"))));
    }

    @Test
    void markingAsSeen_hidesTheNotices() throws Exception {
        notify(tourist, reservation);
        notify(tourist, reservation);

        mockMvc.perform(get("/tourist").with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("Tenés 2 avisos")));

        mockMvc.perform(post("/tourist/notifications/read").with(as(Role.TOURIST, tourist.getId())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tourist"));

        assertThat(notificationRepository.findByTouristIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(tourist.getId()))
                .isEmpty();
        mockMvc.perform(get("/tourist").with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(not(containsString("venció porque"))));
    }

    @Test
    void markingAsSeen_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/tourist/notifications/read").with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void providers_cannotUseIt() throws Exception {
        mockMvc.perform(post("/tourist/notifications/read").with(as(Role.PROVIDER, 999L)).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
