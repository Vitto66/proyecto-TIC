package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CU-15 de punta a punta contra H2, con el servicio de pagos simulado (sin credenciales de
 * Mercado Pago no se sale a internet). El resultado se elige con el titular, como en el sandbox.
 */
@SpringBootTest
@Transactional
class PaymentPageIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);

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
    @Autowired private ReservationPaymentRepository paymentRepository;

    private MockMvc mockMvc;
    private Tourist tourist;
    private Activity activity;
    private ActivityInstance date;
    private Reservation reservation;
    private String reservationUrl;

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

        User touristUser = userRepository.save(User.builder().email("ana@mail.com").passwordHash("x").build());
        tourist = touristRepository.save(Tourist.builder().user(touristUser)
                .firstName("Ana").lastName("Pérez").nationalId("12345678").build());

        activity = activityRepository.save(Activity.builder().business(business).locality(laPaloma)
                .name("Atardecer en kayak").description("Por la laguna").durationMinutes(150)
                .price(new BigDecimal("1500")).minAge(0).bookingCutoffMinutes(60).active(true).build());
        date = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.plusDays(5).withHour(10).withMinute(0)).maxCapacity(10).build());

        reservation = reservation(tourist, 2, ReservationStatus.PENDING, NOW.minusHours(1));
        reservationUrl = "/tourist/reservations/" + reservation.getId();
    }

    private Reservation reservation(Tourist owner, int participants, ReservationStatus status, LocalDateTime createdAt) {
        return reservationRepository.save(Reservation.builder().tourist(owner).instance(date)
                .participants(participants).totalPrice(new BigDecimal("1500").multiply(BigDecimal.valueOf(participants)))
                .status(status).createdAt(createdAt).build());
    }

    private static RequestPostProcessor as(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "ana@mail.com", Role.TOURIST, "Ana");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TOURIST"))));
    }

    private RequestPostProcessor session() {
        return as(tourist.getId());
    }

    private org.springframework.test.web.servlet.ResultActions payAs(String cardholder) throws Exception {
        return mockMvc.perform(post(reservationUrl + "/pay").with(session()).with(csrf())
                .param("cardholderName", cardholder));
    }

    // ---------- Paso 1: la página de pago ----------

    @Test
    void reservationPage_offersToPay() throws Exception {
        mockMvc.perform(get(reservationUrl).with(session()))
                .andExpect(content().string(containsString(reservationUrl + "/pay")))
                .andExpect(content().string(containsString("Pagar")));
    }

    @Test
    void payPage_withoutMercadoPagoCredentials_usesTheSimulatedPayment() throws Exception {
        mockMvc.perform(get(reservationUrl + "/pay").with(session()))
                .andExpect(status().isOk())
                .andExpect(view().name("pay"))
                .andExpect(content().string(containsString("Pago simulado")))
                .andExpect(content().string(containsString("$ 3.000")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("sdk.mercadopago.com"))));
    }

    // ---------- Pasos 3 a 6: pago aprobado ----------

    @Test
    void approvedPayment_confirmsTheReservationAndTakesTheSeats() throws Exception {
        payAs("APRO")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(reservationUrl))
                .andExpect(flash().attribute("paymentLevel", "success"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(paymentRepository.findByReservationIdOrderByIdAsc(reservation.getId()))
                .singleElement().satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.APPROVED);
                    assertThat(p.getAmount()).isEqualByComparingTo("3000");
                    assertThat(p.getProvider()).isEqualTo("SIMULATED");
                    assertThat(p.getTransactionId()).isNotBlank();
                });
        // Ahora sí ocupa lugar
        mockMvc.perform(get("/activities/" + activity.getId()))
                .andExpect(content().string(containsString("8 lugares libres")));
        mockMvc.perform(get(reservationUrl).with(session()))
                .andExpect(content().string(containsString("Confirmada")))
                .andExpect(content().string(containsString("Tu lugar está asegurado")));
    }

    @Test
    void payingAgain_doesNotChargeTwice() throws Exception {
        payAs("APRO");
        payAs("APRO").andExpect(redirectedUrl(reservationUrl));

        assertThat(paymentRepository.findByReservationIdOrderByIdAsc(reservation.getId())).hasSize(1);
    }

    // ---------- 4a y 4b: rechazado o sin respuesta ----------

    @Test
    void rejectedPayment_keepsItPendingAndLetsRetry() throws Exception {
        payAs("FUND")
                .andExpect(redirectedUrl(reservationUrl + "/pay"))
                .andExpect(flash().attribute("paymentMessage", "La tarjeta no tiene fondos suficientes."));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(paymentRepository.findFirstByReservationIdOrderByIdDesc(reservation.getId()))
                .get().extracting(ReservationPayment::getStatus).isEqualTo(PaymentStatus.REJECTED);

        // Reintenta y sale bien
        payAs("APRO").andExpect(flash().attribute("paymentLevel", "success"));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void serviceUnavailable_keepsItPendingAndDoesNotConfirm() throws Exception {
        payAs("ERROR")
                .andExpect(redirectedUrl(reservationUrl + "/pay"))
                .andExpect(flash().attribute("paymentMessage", containsString("no está respondiendo")));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(paymentRepository.findFirstByReservationIdOrderByIdDesc(reservation.getId()))
                .get().extracting(ReservationPayment::getStatus).isEqualTo(PaymentStatus.UNAVAILABLE);
    }

    @Test
    void paymentInProcess_canBeCheckedLater() throws Exception {
        payAs("CONT").andExpect(redirectedUrl(reservationUrl)).andExpect(flash().attribute("paymentLevel", "warning"));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);

        mockMvc.perform(get(reservationUrl).with(session()))
                .andExpect(content().string(containsString("Consultar el estado del pago")));

        // Mientras está en revisión no se puede volver a cobrar
        payAs("APRO").andExpect(flash().attribute("paymentLevel", "warning"));
        assertThat(paymentRepository.findByReservationIdOrderByIdAsc(reservation.getId())).hasSize(1);

        // El simulado lo aprueba al consultarlo
        mockMvc.perform(post(reservationUrl + "/pay/refresh").with(session()).with(csrf()))
                .andExpect(redirectedUrl(reservationUrl))
                .andExpect(flash().attribute("paymentLevel", "success"));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    // ---------- 2a y 2b: ya no se puede ----------

    @Test
    void seatsTakenMeanwhile_doesNotCharge() throws Exception {
        // 2a. Otro turista pagó 9 de los 10 lugares: no entran 2 personas más
        User otherUser = userRepository.save(User.builder().email("otro@mail.com").passwordHash("x").build());
        Tourist other = touristRepository.save(Tourist.builder().user(otherUser)
                .firstName("Otro").lastName("T").nationalId("87654321").build());
        reservation(other, 9, ReservationStatus.CONFIRMED, NOW.minusDays(1));

        payAs("APRO")
                .andExpect(redirectedUrl(reservationUrl))
                .andExpect(flash().attribute("paymentMessage", containsString("No se cobró nada")));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(paymentRepository.findByReservationIdOrderByIdAsc(reservation.getId())).isEmpty();
    }

    @Test
    void after24Hours_cannotBePaid() throws Exception {
        reservation.setCreatedAt(NOW.minusHours(25));

        mockMvc.perform(get(reservationUrl + "/pay").with(session()))
                .andExpect(redirectedUrl(reservationUrl));
        payAs("APRO")
                .andExpect(flash().attribute("paymentMessage", containsString("Pasaron las 24 horas")));
        assertThat(paymentRepository.findByReservationIdOrderByIdAsc(reservation.getId())).isEmpty();
        mockMvc.perform(get(reservationUrl).with(session()))
                .andExpect(content().string(containsString("ya no se puede pagar")));
    }

    @Test
    void bookingClosed_cannotBePaid() throws Exception {
        // 2b. Empieza en 30 minutos y las reservas cierran 60 minutos antes
        date.setStartAt(NOW.plusMinutes(30));

        payAs("APRO")
                .andExpect(flash().attribute("paymentMessage", containsString("Ya no se admiten reservas")));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    // ---------- Quién puede pagar ----------

    @Test
    void anotherTourist_cannotPayIt() throws Exception {
        User otherUser = userRepository.save(User.builder().email("otro@mail.com").passwordHash("x").build());
        Tourist other = touristRepository.save(Tourist.builder().user(otherUser)
                .firstName("Otro").lastName("T").nationalId("87654321").build());

        mockMvc.perform(post(reservationUrl + "/pay").with(as(other.getId())).with(csrf())
                        .param("cardholderName", "APRO"))
                .andExpect(status().isNotFound());
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post(reservationUrl + "/pay").with(session()).param("cardholderName", "APRO"))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutCardData_asksForIt() throws Exception {
        payAs(" ").andExpect(redirectedUrl(reservationUrl + "/pay"))
                .andExpect(flash().attribute("paymentMessage", "Completá los datos de la tarjeta."));
        assertThat(paymentRepository.findByReservationIdOrderByIdAsc(reservation.getId())).isEmpty();
    }
}
