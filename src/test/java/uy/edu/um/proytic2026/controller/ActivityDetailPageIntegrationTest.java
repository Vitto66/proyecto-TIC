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
import uy.edu.um.proytic2026.dto.DailyForecast;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;
import uy.edu.um.proytic2026.service.WeatherService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CU-06 de punta a punta, contra H2 y con Spring Security activo. El clima se reemplaza por uno
 * simulado: los tests no dependen de internet ni de la API.
 */
@SpringBootTest
@Transactional
class ActivityDetailPageIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);
    /** Dentro de los 16 días que pronostica la API. */
    private static final LocalDate SOON = NOW.toLocalDate().plusDays(3);
    /** Más allá del pronóstico. */
    private static final LocalDate FAR = NOW.toLocalDate().plusDays(40);

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
    @Autowired private ReviewRepository reviewRepository;

    private MockMvc mockMvc;
    private Provider provider;
    private Tourist tourist;
    private Activity activity;
    private String url;

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

        User touristUser = userRepository.save(User.builder().email("ana@mail.com").passwordHash("x").build());
        tourist = touristRepository.save(Tourist.builder().user(touristUser)
                .firstName("Ana").lastName("Pérez").nationalId("12345678").build());

        ActivityType acuaticas = activityTypeRepository.save(ActivityType.builder().name("Actividades acuáticas").build());
        EquipmentType chaleco = equipmentTypeRepository.save(EquipmentType.builder().name("Chaleco salvavidas").build());
        EquipmentType gorro = equipmentTypeRepository.save(EquipmentType.builder().name("Gorro").build());

        // Dura 2 h 30 min; las reservas cierran 60 minutos antes
        activity = Activity.builder().business(business).locality(laPaloma).name("Atardecer en kayak")
                .description("Recorrido por la laguna").durationMinutes(150).price(new BigDecimal("1500"))
                .minAge(12).bookingCutoffMinutes(60).active(true).build();
        activity.getTypes().add(acuaticas);
        activity.addEquipment(chaleco, true);
        activity.addEquipment(gorro, false);
        activity = activityRepository.save(activity);
        url = "/activities/" + activity.getId();
    }

    // ---------- Datos y sesiones de prueba ----------

    private ActivityInstance date(LocalDateTime start, Integer capacity) {
        return instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(start).maxCapacity(capacity).build());
    }

    private Reservation reservation(ActivityInstance date, int participants, ReservationStatus status) {
        return reservationRepository.save(Reservation.builder().tourist(tourist).instance(date)
                .participants(participants).totalPrice(new BigDecimal("1500")).status(status)
                .createdAt(NOW.minusDays(20)).build());
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "X");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    // ---------- Paso 2: la información de la actividad ----------

    @Test
    void detail_isPublicAndShowsEverythingNeededToDecide() throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-detail"))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                .andExpect(content().string(containsString("Kayak Laguna")))          // emprendimiento organizador
                .andExpect(content().string(containsString("Recorrido por la laguna"))) // descripción
                .andExpect(content().string(containsString("La Paloma, Rocha")))      // ubicación
                .andExpect(content().string(containsString("2 h 30 min")))            // duración
                .andExpect(content().string(containsString("$ 1.500")))               // precio
                .andExpect(content().string(containsString("Actividades acuáticas"))) // tipo
                .andExpect(content().string(containsString("Desde 12 años")))         // edad mínima
                .andExpect(content().string(containsString("Tenés que traer")))       // equipamiento necesario
                .andExpect(content().string(containsString("Gorro")))
                .andExpect(content().string(containsString("Lo pone el prestador")))  // equipamiento provisto
                .andExpect(content().string(containsString("Chaleco salvavidas")));
    }

    @Test
    void inactiveOrUnknownActivity_isNotFound() throws Exception {
        activity.setActive(false);
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        mockMvc.perform(get("/activities/999999")).andExpect(status().isNotFound());
    }

    // ---------- Paso 3 y 3a: próximas fechas ----------

    @Test
    void upcomingDates_showAvailability() throws Exception {
        date(FAR.atTime(10, 0), 10);
        ActivityInstance full = date(FAR.atTime(16, 0), 4);
        reservation(full, 4, ReservationStatus.CONFIRMED);
        // Una pasada no se muestra
        date(NOW.minusDays(2), null);

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("10 lugares libres")))
                .andExpect(content().string(containsString("Completa")))
                .andExpect(content().string(containsString("10:00 a 12:30")));
    }

    @Test
    void dateWithinTheBookingCutoff_showsReservationsClosed() throws Exception {
        // Empieza en 30 minutos y las reservas cierran 60 minutos antes
        date(NOW.plusMinutes(30), null);

        mockMvc.perform(get(url))
                .andExpect(content().string(containsString("Reservas cerradas")));
    }

    @Test
    void withoutFutureDates_saysSoAndDoesNotAskForTheWeather() throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No hay fechas disponibles")));

        verify(weatherService, never()).forecast(any(), any());
    }

    // ---------- Paso 5 y 5a: el clima ----------

    @Test
    void forecast_isShownForDatesWithinTheForecastRange() throws Exception {
        date(SOON.atTime(10, 0), null);
        when(weatherService.forecast("La Paloma", "Rocha"))
                .thenReturn(Map.of(SOON, new DailyForecast(SOON, 2, 24.4, 15.2, 10)));

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Parcialmente nublado")))
                .andExpect(content().string(containsString("24° / 15°")))
                .andExpect(content().string(containsString("10 % de lluvia")))
                .andExpect(content().string(containsString("Open-Meteo")));
    }

    @Test
    void forecastUnavailable_detailIsShownWithoutTheWeather() throws Exception {
        // 5a. La API no respondió: el servicio devuelve un pronóstico vacío
        date(SOON.atTime(10, 0), null);
        when(weatherService.forecast(any(), any())).thenReturn(Map.of());

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Atardecer en kayak")))
                .andExpect(content().string(containsString("no está disponible")));
    }

    @Test
    void datesTooFarAway_doNotCallTheWeatherApi() throws Exception {
        // 5a. Fecha demasiado lejana para el pronóstico
        date(FAR.atTime(10, 0), null);

        mockMvc.perform(get(url))
                .andExpect(content().string(containsString("no está disponible")));
        verify(weatherService, never()).forecast(any(), any());
    }

    // ---------- Paso 4: valoraciones ----------

    @Test
    void reviews_showAuthorRatingAndComment() throws Exception {
        ActivityInstance past = date(NOW.minusDays(15), null);
        Reservation finished = reservation(past, 2, ReservationStatus.FINISHED);
        reviewRepository.save(Review.builder().reservation(finished).rating(4)
                .comment("Muy linda experiencia").createdAt(NOW.minusDays(14)).build());

        mockMvc.perform(get(url))
                .andExpect(content().string(containsString("Ana P.")))
                .andExpect(content().string(containsString("★★★★☆")))
                .andExpect(content().string(containsString("Muy linda experiencia")))
                .andExpect(content().string(containsString("4,0")));
    }

    @Test
    void withoutReviews_saysSo() throws Exception {
        mockMvc.perform(get(url))
                .andExpect(content().string(containsString("Todavía nadie valoró esta actividad")));
    }

    // ---------- 1a. Reservar requiere ser turista ----------

    @Test
    void visitorWithoutSession_isAskedToLogInAsTourist() throws Exception {
        mockMvc.perform(get(url))
                .andExpect(content().string(containsString("Iniciar sesión para reservar")));
    }

    @Test
    void tourist_seesTheReserveButton() throws Exception {
        date(FAR.atTime(10, 0), null);

        mockMvc.perform(get(url).with(as(Role.TOURIST, tourist.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Reservar<")))
                .andExpect(content().string(not(containsString("Iniciar sesión para reservar"))));
    }

    @Test
    void providerProfile_isToldToUseTheTouristProfile() throws Exception {
        mockMvc.perform(get(url).with(as(Role.PROVIDER, provider.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("perfil de turista")));
    }

    // ---------- Desde la vidriera ----------

    @Test
    void catalogCard_leadsToThisDetail() throws Exception {
        mockMvc.perform(get("/activities"))
                .andExpect(content().string(containsString("href=\"" + url + "\"")));
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }
}
