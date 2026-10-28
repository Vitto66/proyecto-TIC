package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.CatalogResult;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * CU-05 de punta a punta, contra H2 y con Spring Security activo: la vidriera es pública, muestra
 * solo lo publicado, ordena por calificación y aplica los filtros que llegan en la URL.
 */
@SpringBootTest
@Transactional
class CatalogPageIntegrationTest {

    private static final String URL = "/activities";
    private static final LocalDate FUTURE_DAY = LocalDate.now(ClockConfig.URUGUAY).plusDays(30);

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

    private Department rocha;
    private Department maldonado;
    private Locality laPaloma;
    private Locality puntaDelEste;
    private ActivityType acuaticas;
    private ActivityType cabalgatas;
    private Business laguna;
    private Business estancia;
    private Tourist tourist;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        maldonado = departmentRepository.save(Department.builder().name("Maldonado").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        puntaDelEste = localityRepository.save(Locality.builder().department(maldonado).name("Punta del Este").build());
        acuaticas = activityTypeRepository.save(ActivityType.builder().name("Actividades acuáticas").build());
        cabalgatas = activityTypeRepository.save(ActivityType.builder().name("Cabalgatas").build());

        laguna = business("laguna@mail.com", "11111111", "Kayak Laguna", laPaloma);
        estancia = business("estancia@mail.com", "22222222", "Estancia Sierra", puntaDelEste);

        User touristUser = userRepository.save(User.builder().email("ana@mail.com").passwordHash("x").build());
        tourist = touristRepository.save(Tourist.builder().user(touristUser)
                .firstName("Ana").lastName("Pérez").nationalId("12345678").build());
    }

    // ---------- Datos de prueba ----------

    private Business business(String email, String document, String name, Locality locality) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder().user(user).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document(document).address("x").build());
        return businessRepository.save(Business.builder().provider(provider).locality(locality)
                .name(name).description("x").address("x").phone("099").email(email).build());
    }

    private Activity activity(Business business, Locality locality, String name, String price, ActivityType type, boolean active) {
        Activity a = Activity.builder().business(business).locality(locality).name(name)
                .description("Una linda salida").durationMinutes(120).price(new BigDecimal(price))
                .minAge(0).bookingCutoffMinutes(0).active(active).build();
        a.getTypes().add(type);
        return activityRepository.save(a);
    }

    /** Una reserva finalizada con su valoración. */
    private void review(Activity activity, int rating) {
        ActivityInstance past = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(LocalDateTime.now(ClockConfig.URUGUAY).minusDays(10).withNano(0).plusMinutes(rating)).build());
        Reservation reservation = reservationRepository.save(Reservation.builder().tourist(tourist).instance(past)
                .participants(1).totalPrice(activity.getPrice()).status(ReservationStatus.FINISHED)
                .createdAt(LocalDateTime.now(ClockConfig.URUGUAY).minusDays(12)).build());
        reviewRepository.save(Review.builder().reservation(reservation).rating(rating)
                .comment("Muy bueno").createdAt(LocalDateTime.now(ClockConfig.URUGUAY).minusDays(9)).build());
    }

    private CatalogResult result(org.springframework.test.web.servlet.MvcResult mvc) {
        return (CatalogResult) mvc.getModelAndView().getModel().get("result");
    }

    private List<String> names(String query) throws Exception {
        CatalogResult result = result(mockMvc.perform(get(URL + query)).andExpect(status().isOk()).andReturn());
        return result.activities().stream().map(c -> c.name()).toList();
    }

    // ---------- Público ----------

    @Test
    void isPublic_withoutAccount() throws Exception {
        activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog"))
                .andExpect(content().string(containsString("Explorá actividades")))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                .andExpect(content().string(containsString("Kayak Laguna")))
                .andExpect(content().string(containsString("Iniciar sesión")));
    }

    @Test
    void isAlsoAvailableForATourist() throws Exception {
        activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);
        AuthenticatedUser principal = new AuthenticatedUser(tourist.getId(), "ana@mail.com", Role.TOURIST, "Ana");

        mockMvc.perform(get(URL).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_TOURIST"))))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi espacio")));
    }

    @Test
    void homeMenu_linksToTheCatalog() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Explorar actividades")))
                .andExpect(content().string(containsString("href=\"/activities\"")));
    }

    // ---------- 2a y 4a ----------

    @Test
    void noPublishedActivities_showsMessage() throws Exception {
        // Una dada de baja no cuenta como publicada
        activity(laguna, laPaloma, "Vieja actividad", "1500", acuaticas, false);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no hay actividades publicadas")))
                .andExpect(content().string(not(containsString("Vieja actividad"))));
    }

    @Test
    void noMatches_showsMessage() throws Exception {
        activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);

        mockMvc.perform(get(URL).param("keyword", "paracaidismo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No se encontraron actividades")));
    }

    // ---------- 3a. Orden por calificación ----------

    @Test
    void noFilters_orderedFromBestToWorstRated() throws Exception {
        Activity kayak = activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);
        Activity cabalgata = activity(estancia, puntaDelEste, "Cabalgata serrana", "3000", cabalgatas, true);
        activity(estancia, puntaDelEste, "Sin valorar", "500", cabalgatas, true);
        review(kayak, 3);
        review(kayak, 4);
        review(cabalgata, 5);

        assertThat(names("")).containsExactly("Cabalgata serrana", "Atardecer en kayak", "Sin valorar");

        mockMvc.perform(get(URL))
                .andExpect(content().string(containsString("3,5")))
                .andExpect(content().string(containsString("2 valoraciones")))
                .andExpect(content().string(containsString("Sin valoraciones todavía")));
    }

    // ---------- Paso 3: filtros por la URL ----------

    @Test
    void filtersFromTheUrl() throws Exception {
        Activity kayak = activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);
        Activity cabalgata = activity(estancia, puntaDelEste, "Cabalgata serrana", "3000", cabalgatas, true);
        review(cabalgata, 5);
        instanceRepository.save(ActivityInstance.builder().activity(kayak)
                .startAt(FUTURE_DAY.atTime(10, 0)).maxCapacity(10).build());

        assertThat(names("?departmentId=" + maldonado.getId())).containsExactly("Cabalgata serrana");
        assertThat(names("?localityId=" + laPaloma.getId())).containsExactly("Atardecer en kayak");
        assertThat(names("?typeId=" + acuaticas.getId())).containsExactly("Atardecer en kayak");
        assertThat(names("?date=" + FUTURE_DAY)).containsExactly("Atardecer en kayak");
        assertThat(names("?minPrice=2000")).containsExactly("Cabalgata serrana");
        assertThat(names("?maxPrice=2000")).containsExactly("Atardecer en kayak");
        assertThat(names("?minRating=4")).containsExactly("Cabalgata serrana");
        assertThat(names("?keyword=ESTANCIA")).containsExactly("Cabalgata serrana");
    }

    @Test
    void invalidFilterValue_isIgnoredWithANotice() throws Exception {
        activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);

        mockMvc.perform(get(URL).param("minPrice", "barato"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no se aplicó")))
                .andExpect(content().string(containsString("Atardecer en kayak")));
    }

    @Test
    void cardsLinkToTheDetail() throws Exception {
        Activity kayak = activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);

        mockMvc.perform(get(URL))
                .andExpect(content().string(containsString("/activities/" + kayak.getId())));
    }

    // ---------- Vista de calendario ----------

    @Test
    void calendarView_showsTheAvailableDatesOfTheMonth() throws Exception {
        Activity kayak = activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);
        Activity cabalgata = activity(estancia, puntaDelEste, "Cabalgata serrana", "3000", cabalgatas, true);
        instanceRepository.save(ActivityInstance.builder().activity(kayak)
                .startAt(FUTURE_DAY.atTime(10, 0)).maxCapacity(10).build());
        String month = java.time.YearMonth.from(FUTURE_DAY).toString();

        org.springframework.test.web.servlet.MvcResult mvc = mockMvc.perform(get(URL)
                        .param("view", "calendar").param("month", month))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog"))
                .andExpect(content().string(containsString("1 fecha disponible")))
                .andExpect(content().string(containsString("10:00")))
                .andExpect(content().string(containsString("/activities/" + kayak.getId())))
                .andReturn();
        uy.edu.um.proytic2026.dto.CatalogCalendar calendar =
                (uy.edu.um.proytic2026.dto.CatalogCalendar) mvc.getModelAndView().getModel().get("calendar");
        assertThat(calendar.month()).isEqualTo(month);
        assertThat(calendar.weeks()).allSatisfy(week -> assertThat(week).hasSize(7));
        // La cabalgata no tiene fechas: no aparece en el calendario
        assertThat(calendar.weeks().stream().flatMap(List::stream).flatMap(d -> d.slots().stream()))
                .extracting(uy.edu.um.proytic2026.dto.CatalogCalendar.Slot::activityName)
                .containsExactly("Atardecer en kayak");
        assertThat(cabalgata.getId()).isNotNull();
    }

    @Test
    void calendarView_appliesTheFilters_andKeepsThemInTheMonthLinks() throws Exception {
        Activity kayak = activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);
        instanceRepository.save(ActivityInstance.builder().activity(kayak)
                .startAt(FUTURE_DAY.atTime(10, 0)).maxCapacity(10).build());
        String month = java.time.YearMonth.from(FUTURE_DAY).toString();

        mockMvc.perform(get(URL).param("view", "calendar").param("month", month)
                        .param("departmentId", maldonado.getId().toString()))
                .andExpect(content().string(containsString("0 fechas disponibles")))
                .andExpect(content().string(containsString("departmentId=" + maldonado.getId())));
    }

    @Test
    void listView_offersTheCalendar() throws Exception {
        activity(laguna, laPaloma, "Atardecer en kayak", "1500", acuaticas, true);

        mockMvc.perform(get(URL))
                .andExpect(content().string(containsString("view=calendar")))
                .andExpect(content().string(containsString("Calendario")));
    }
}
