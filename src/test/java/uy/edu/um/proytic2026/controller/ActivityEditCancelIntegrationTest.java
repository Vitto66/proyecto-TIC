package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
 * CU-10 de punta a punta, contra H2 y con Spring Security activo: modificar una actividad y darla
 * de baja, con la cancelación y el reembolso de sus reservas (servicio de pagos simulado).
 */
@SpringBootTest
@Transactional
class ActivityEditCancelIntegrationTest {

    private static final String URL = "/provider/activities";
    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);

    @MockitoBean private WeatherService weatherService;

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private TouristRepository touristRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LocalityRepository localityRepository;
    @Autowired private ActivityTypeRepository activityTypeRepository;
    @Autowired private EquipmentTypeRepository equipmentTypeRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityInstanceRepository instanceRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationPaymentRepository paymentRepository;

    private MockMvc mockMvc;
    private Department rocha;
    private Locality laPaloma;
    private Locality cabo;
    private ActivityType kayak;
    private ActivityType naturaleza;
    private EquipmentType chaleco;
    private EquipmentType remo;
    private EquipmentType gorro;
    private Provider provider;
    private Tourist tourist;
    private Activity activity;
    private ActivityInstance nextWeek;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        cabo = localityRepository.save(Locality.builder().department(rocha).name("Cabo Polonio").build());
        kayak = activityTypeRepository.save(ActivityType.builder().name("Actividades acuáticas").build());
        naturaleza = activityTypeRepository.save(ActivityType.builder().name("Turismo de naturaleza").build());
        chaleco = equipmentTypeRepository.save(EquipmentType.builder().name("Chaleco salvavidas").build());
        remo = equipmentTypeRepository.save(EquipmentType.builder().name("Remo").build());
        gorro = equipmentTypeRepository.save(EquipmentType.builder().name("Gorro").build());

        User providerUser = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        provider = providerRepository.save(Provider.builder().user(providerUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        providerUser.setProvider(provider);
        Business business = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("x").address("x").phone("099").email("a@b.uy").build());

        User touristUser = userRepository.save(User.builder().email("ana@mail.com").passwordHash("x").build());
        tourist = touristRepository.save(Tourist.builder().user(touristUser)
                .firstName("Ana").lastName("Pérez").nationalId("12345678").build());
        touristUser.setTourist(tourist);

        activity = Activity.builder().business(business).locality(laPaloma).name("Atardecer en kayak")
                .description("Por la laguna").durationMinutes(120).price(new BigDecimal("1500")).minAge(0)
                .bookingCutoffMinutes(0).active(true).build();
        activity.getTypes().add(kayak);
        activity.addEquipment(chaleco, true);
        activity.addEquipment(remo, true);
        activity = activityRepository.save(activity);
        nextWeek = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.plusDays(7)).maxCapacity(10).build());
    }

    // ---------- Datos y sesiones de prueba ----------

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Juan");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private RequestPostProcessor providerSession() {
        return as(Role.PROVIDER, provider.getId());
    }

    private Reservation reservation(ActivityInstance instance, int people, ReservationStatus status) {
        return reservationRepository.save(Reservation.builder().tourist(tourist).instance(instance)
                .participants(people).totalPrice(new BigDecimal("1500").multiply(BigDecimal.valueOf(people)))
                .status(status).createdAt(NOW.minusHours(1)).build());
    }

    private void paid(Reservation r) {
        paymentRepository.save(ReservationPayment.builder().reservation(r).provider("SIMULATED")
                .transactionId("sim-" + r.getId()).status(PaymentStatus.APPROVED).statusDetail("accredited")
                .amount(r.getTotalPrice()).createdAt(NOW.minusHours(1)).build());
    }

    /** El formulario completo, como lo manda el navegador al guardar. */
    private MockHttpServletRequestBuilder update(String price) {
        return update("Kayak al atardecer", price);
    }

    /** Cada parámetro va una sola vez: si se repite, Spring junta los valores con una coma. */
    private MockHttpServletRequestBuilder update(String name, String price) {
        return post(URL + "/" + activity.getId()).with(providerSession()).with(csrf())
                .param("name", name).param("description", "Nueva descripción")
                .param("departmentId", rocha.getId().toString()).param("localityId", cabo.getId().toString())
                .param("durationMinutes", "150").param("price", price).param("minAge", "12")
                .param("bookingCutoffMinutes", "60")
                .param("activityTypeIds", kayak.getId().toString(), naturaleza.getId().toString())
                // el chaleco ahora lo trae el turista, el remo ya no se usa y se agrega el gorro
                .param("equipment[" + chaleco.getId() + "]", "TOURIST")
                .param("equipment[" + remo.getId() + "]", "")
                .param("equipment[" + gorro.getId() + "]", "PROVIDER");
    }

    private ResultActions cancel() throws Exception {
        return mockMvc.perform(post(URL + "/" + activity.getId() + "/cancel").with(providerSession()).with(csrf()));
    }

    // ---------- Paso 1: elegir la actividad ----------

    @Test
    void myActivities_offersToModifyOrCancelEach() throws Exception {
        mockMvc.perform(get(URL).with(providerSession()))
                .andExpect(content().string(containsString(URL + "/" + activity.getId() + "/edit")))
                .andExpect(content().string(containsString(URL + "/" + activity.getId() + "/cancel")));
    }

    // ---------- Modificar ----------

    @Test
    void editForm_showsTheCurrentData() throws Exception {
        mockMvc.perform(get(URL + "/" + activity.getId() + "/edit").with(providerSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-form"))
                .andExpect(content().string(containsString("Modificar")))
                .andExpect(content().string(containsString("value=\"Atardecer en kayak\"")))
                .andExpect(content().string(containsString("Guardar cambios")))
                .andExpect(content().string(containsString("rige solo para las reservas nuevas")))
                .andExpect(content().string(containsString("action=\"" + URL + "/" + activity.getId() + "\"")));
    }

    @Test
    void update_changesTheActivity_includingTypesAndEquipment() throws Exception {
        mockMvc.perform(update("1800"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(URL))
                .andExpect(flash().attribute("activityMessage", containsString("Kayak al atardecer")));

        Activity saved = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Kayak al atardecer");
        assertThat(saved.getLocality().getId()).isEqualTo(cabo.getId());
        assertThat(saved.getPrice()).isEqualByComparingTo("1800");
        assertThat(saved.getDurationMinutes()).isEqualTo(150);
        assertThat(saved.getBookingCutoffMinutes()).isEqualTo(60);
        assertThat(saved.getTypes()).containsExactlyInAnyOrder(kayak, naturaleza);
        assertThat(saved.getEquipment()).extracting(e -> e.getEquipmentType().getName() + ":" + e.isProvidedByProvider())
                .containsExactlyInAnyOrder("Chaleco salvavidas:false", "Gorro:true");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void priceChange_onlyAppliesToNewReservations() throws Exception {
        // 3a. La reserva ya hecha mantiene el precio original
        Reservation before = reservation(nextWeek, 2, ReservationStatus.CONFIRMED);

        mockMvc.perform(update("1800")).andExpect(status().is3xxRedirection());

        assertThat(before.getTotalPrice()).isEqualByComparingTo("3000");
    }

    @Test
    void update_withMissingData_marksTheFields() throws Exception {
        mockMvc.perform(update("", "1800"))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-form"))
                .andExpect(model().attributeHasFieldErrors("form", "name"));
        assertThat(activityRepository.findById(activity.getId()).orElseThrow().getName()).isEqualTo("Atardecer en kayak");
    }

    @Test
    void update_keepsATypeTheAdminDeactivatedMeanwhile() throws Exception {
        kayak.setActive(false);

        mockMvc.perform(update("1800")).andExpect(status().is3xxRedirection());

        assertThat(activityRepository.findById(activity.getId()).orElseThrow().getTypes()).contains(kayak);
    }

    @Test
    void anotherProvidersActivity_isNotFound() throws Exception {
        User otherUser = userRepository.save(User.builder().email("otro@mail.com").passwordHash("x").build());
        Provider other = providerRepository.save(Provider.builder().user(otherUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Otro").lastName("P").document("11111111").address("x").build());

        mockMvc.perform(get(URL + "/" + activity.getId() + "/edit").with(as(Role.PROVIDER, other.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(URL + "/" + activity.getId() + "/cancel").with(as(Role.PROVIDER, other.getId())).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(activity.isActive()).isTrue();
    }

    @Test
    void touristProfile_cannotModifyActivities() throws Exception {
        mockMvc.perform(get(URL + "/" + activity.getId() + "/edit").with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isForbidden());
    }

    // ---------- Dar de baja ----------

    @Test
    void cancelPage_withoutReservations_justAsksToConfirm() throws Exception {
        mockMvc.perform(get(URL + "/" + activity.getId() + "/cancel").with(providerSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-cancel"))
                .andExpect(content().string(containsString("no afecta a ningún turista")));
    }

    @Test
    void cancelPage_tellsHowManyReservationsAreAffected() throws Exception {
        // 2a. Se informa la cantidad y se pide confirmación
        reservation(nextWeek, 3, ReservationStatus.CONFIRMED);
        reservation(nextWeek, 2, ReservationStatus.CONFIRMED);
        reservation(nextWeek, 1, ReservationStatus.PENDING);

        mockMvc.perform(get(URL + "/" + activity.getId() + "/cancel").with(providerSession()))
                .andExpect(content().string(containsString("2 reservas pagas (5 personas)")))
                .andExpect(content().string(containsString("se le devuelve el 100% del pago")))
                .andExpect(content().string(containsString("1 reserva pendiente de pago")));
        // Mirar la confirmación no cambia nada
        assertThat(activity.isActive()).isTrue();
    }

    @Test
    void cancel_withdrawsIt_cancelsUpcomingReservations_andRefundsThePaidOnes() throws Exception {
        Reservation confirmed = reservation(nextWeek, 2, ReservationStatus.CONFIRMED);
        paid(confirmed);
        Reservation pending = reservation(nextWeek, 1, ReservationStatus.PENDING);
        // Una fecha que ya pasó no se toca
        ActivityInstance lastWeek = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.minusDays(7)).maxCapacity(10).build());
        Reservation finished = reservation(lastWeek, 2, ReservationStatus.FINISHED);

        cancel().andExpect(redirectedUrl(URL))
                .andExpect(flash().attribute("activityLevel", "success"))
                .andExpect(flash().attribute("activityMessage", containsString("Se cancelaron 2 reservas")))
                .andExpect(flash().attribute("activityMessage", containsString("Se devolvió el 100% del pago de 1 reserva")));

        assertThat(activity.isActive()).isFalse();
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(finished.getStatus()).isEqualTo(ReservationStatus.FINISHED);
        assertThat(paymentRepository.findFirstByReservationIdOrderByIdDesc(confirmed.getId()))
                .get().extracting(ReservationPayment::getStatus).isEqualTo(PaymentStatus.REFUNDED);

        // Sale del portal y ya no se puede modificar
        mockMvc.perform(get("/activities"))
                .andExpect(content().string(not(containsString("Atardecer en kayak"))));
        mockMvc.perform(get(URL).with(providerSession()))
                .andExpect(content().string(containsString("Dada de baja")))
                .andExpect(content().string(not(containsString(URL + "/" + activity.getId() + "/edit"))));
        mockMvc.perform(get(URL + "/" + activity.getId() + "/edit").with(providerSession()))
                .andExpect(redirectedUrl(URL));
    }

    @Test
    void tourist_seesTheReservationCancelledAndRefunded() throws Exception {
        Reservation confirmed = reservation(nextWeek, 2, ReservationStatus.CONFIRMED);
        paid(confirmed);
        Reservation pending = reservation(nextWeek, 1, ReservationStatus.PENDING);

        cancel();

        mockMvc.perform(get("/tourist/reservations/" + confirmed.getId()).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(containsString("se canceló")))
                .andExpect(content().string(containsString("Se te devolvió el 100% del pago")))
                .andExpect(content().string(not(containsString("/pay\""))));
        mockMvc.perform(get("/tourist/reservations/" + pending.getId()).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(content().string(containsString("No se te cobró nada")));
    }

    @Test
    void cancelledActivity_cannotBeCancelledAgain_norGetNewDates() throws Exception {
        cancel();

        cancel().andExpect(redirectedUrl(URL))
                .andExpect(flash().attribute("activityMessage", containsString("dada de baja")));
    }
}
