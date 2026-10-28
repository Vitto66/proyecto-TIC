package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.HomeOverview;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * El inicio: el mapa marca los departamentos con actividades publicadas y enlaza a la vidriera
 * filtrada; las tarjetas muestran los tipos de actividad. Contra H2 y con Spring Security activo.
 */
@SpringBootTest
@Transactional
class HomePageIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LocalityRepository localityRepository;
    @Autowired private ActivityTypeRepository activityTypeRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityInstanceRepository instanceRepository;

    private MockMvc mockMvc;
    private Department rocha;
    private Locality laPaloma;
    private ActivityType acuaticas;
    private ActivityType cabalgatas;
    private Business laguna;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        departmentRepository.save(Department.builder().name("Maldonado").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        acuaticas = activityTypeRepository.save(ActivityType.builder().name("Actividades acuáticas").build());
        cabalgatas = activityTypeRepository.save(ActivityType.builder().name("Cabalgatas").build());

        User user = userRepository.save(User.builder().email("laguna@mail.com").passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder().user(user).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("11111111").address("x").build());
        laguna = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("x").address("x").phone("099").email("laguna@mail.com").build());
    }

    private Activity activity(String name, ActivityType type, boolean active) {
        Activity a = Activity.builder().business(laguna).locality(laPaloma).name(name)
                .description("Una linda salida").durationMinutes(120).price(new BigDecimal("1500"))
                .minAge(0).bookingCutoffMinutes(0).active(active).build();
        a.getTypes().add(type);
        return activityRepository.save(a);
    }

    private HomeOverview overview() throws Exception {
        MvcResult result = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        return (HomeOverview) result.getModelAndView().getModel().get("overview");
    }

    @Test
    void map_marksDepartmentsWithPublishedActivitiesAndLinksToTheCatalog() throws Exception {
        activity("Atardecer en kayak", acuaticas, true);
        activity("Paseo por la laguna", acuaticas, true);
        activity("Dada de baja", acuaticas, false);

        HomeOverview overview = overview();
        assertThat(overview.totalActivities()).isEqualTo(2);
        assertThat(overview.pins()).singleElement().satisfies(pin -> {
            assertThat(pin.name()).isEqualTo("Rocha");
            assertThat(pin.activityCount()).isEqualTo(2);
            assertThat(pin.departmentId()).isEqualTo(rocha.getId());
        });

        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("Mapa de los departamentos de Uruguay")))
                .andExpect(content().string(containsString("/activities?departmentId=" + rocha.getId())))
                .andExpect(content().string(containsString("2 actividades en 1 departamento")));
    }

    @Test
    void map_withoutActivities_saysThereIsNothingYet() throws Exception {
        assertThat(overview().pins()).isEmpty();

        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("Todavía no hay actividades publicadas")))
                .andExpect(content().string(not(containsString("class=\"map-pin\""))));
    }

    @Test
    void typeCards_showTypesWithMostActivitiesFirstAndLinkToTheCatalog() throws Exception {
        activity("Atardecer en kayak", acuaticas, true);

        HomeOverview overview = overview();
        assertThat(overview.types()).extracting(HomeOverview.TypeCard::name)
                .startsWith("Actividades acuáticas");
        assertThat(overview.types().get(0).icon()).isEqualTo("waves");

        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("Elegí tu forma de recorrer Uruguay")))
                .andExpect(content().string(containsString("/activities?typeId=" + acuaticas.getId())))
                .andExpect(content().string(containsString("/activities?typeId=" + cabalgatas.getId())));
    }

    @Test
    void footer_showsOasisLogoAndPhotoCredits() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("/img/oasis-logo-light.png")))
                .andExpect(content().string(containsString("Desarrollado por")))
                .andExpect(content().string(containsString("Wikimedia Commons")));
    }

    @Test
    void mapPanel_showsTheActivitiesOfTheDepartment_andLinksToThem() throws Exception {
        activity("Atardecer en kayak", acuaticas, true);
        activity("Paseo por la laguna", cabalgatas, true);

        HomeOverview overview = overview();
        String rochaIso = overview.selectedIso();
        assertThat(overview.panel(rochaIso)).extracting(uy.edu.um.proytic2026.dto.HomeActivity::name)
                .containsExactlyInAnyOrder("Atardecer en kayak", "Paseo por la laguna");

        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("Ver las 2 actividades de Rocha")))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                // Buscador: departamentos y tipos para filtrar la vidriera
                .andExpect(content().string(containsString("name=\"departmentId\"")))
                .andExpect(content().string(containsString("name=\"typeId\"")));
    }

    @Test
    void featured_withoutBookingsThisMonth_showsTheHighlightedOnes() throws Exception {
        activity("Atardecer en kayak", acuaticas, true);

        HomeOverview overview = overview();
        assertThat(overview.featuredByBookings()).isFalse();
        assertThat(overview.featured()).singleElement()
                .satisfies(a -> assertThat(a.scene()).isEqualTo("rio"));

        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("Actividades destacadas")))
                .andExpect(content().string(containsString("Desde")));
    }

    @Test
    void upcoming_showsTheNextDateOfEachActivity_soonestFirst() throws Exception {
        LocalDate today = LocalDate.now(ClockConfig.URUGUAY);
        Activity kayak = activity("Atardecer en kayak", acuaticas, true);
        Activity cabalgata = activity("Cabalgata por la sierra", cabalgatas, true);
        activity("Sin fechas", acuaticas, true);
        instanceRepository.save(ActivityInstance.builder().activity(kayak)
                .startAt(today.plusDays(9).atTime(10, 0)).maxCapacity(3).build());
        instanceRepository.save(ActivityInstance.builder().activity(kayak)
                .startAt(today.plusDays(20).atTime(10, 0)).maxCapacity(3).build());
        instanceRepository.save(ActivityInstance.builder().activity(cabalgata)
                .startAt(today.plusDays(4).atTime(16, 30)).maxCapacity(12).build());
        // Una fecha que ya pasó no cuenta
        instanceRepository.save(ActivityInstance.builder().activity(cabalgata)
                .startAt(LocalDateTime.now(ClockConfig.URUGUAY).minusDays(1).withNano(0)).maxCapacity(12).build());

        HomeOverview overview = overview();
        assertThat(overview.upcoming()).extracting(u -> u.activity().name())
                .containsExactly("Cabalgata por la sierra", "Atardecer en kayak");
        assertThat(overview.upcoming().get(1).seatsLabel()).isEqualTo("Quedan 3 lugares");
        assertThat(overview.upcoming().get(1).fewSeats()).isTrue();

        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("Lo más próximo")))
                .andExpect(content().string(containsString("16:30")))
                .andExpect(content().string(containsString("Quedan 12 lugares")));
    }

    @Test
    void upcoming_withoutDates_isNotShown() throws Exception {
        activity("Atardecer en kayak", acuaticas, true);

        assertThat(overview().upcoming()).isEmpty();
        mockMvc.perform(get("/"))
                .andExpect(content().string(not(containsString("Lo más próximo"))));
    }
}
