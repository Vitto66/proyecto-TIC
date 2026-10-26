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
 * CU-13 de punta a punta, contra H2 y con Spring Security activo: consultar, dar de alta,
 * cambiar el nombre, desactivar, eliminar, y quién puede hacerlo.
 */
@SpringBootTest
@Transactional
class AdminTypeIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LocalityRepository localityRepository;
    @Autowired private ActivityTypeRepository activityTypeRepository;
    @Autowired private EquipmentTypeRepository equipmentTypeRepository;
    @Autowired private ActivityRepository activityRepository;

    private MockMvc mockMvc;
    private ActivityType trekking;
    private ActivityType aves;
    private EquipmentType casco;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        trekking = activityTypeRepository.save(ActivityType.builder().name("Trekking").build());
        aves = activityTypeRepository.save(ActivityType.builder().name("Avistamiento de aves").build());
        casco = equipmentTypeRepository.save(EquipmentType.builder().name("Casco").build());

        // Una actividad que usa "Trekking" y "Casco"
        Department lavalleja = departmentRepository.save(Department.builder().name("Lavalleja").build());
        Locality minas = localityRepository.save(Locality.builder().department(lavalleja).name("Minas").build());
        User user = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder().user(user).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        Business business = businessRepository.save(Business.builder().provider(provider).locality(minas)
                .name("Sierras Aventura").description("x").address("x").phone("099").email("a@b.uy").build());
        Activity activity = Activity.builder().business(business).locality(minas).name("Cerro Arequita")
                .description("x").durationMinutes(120).price(new BigDecimal("800")).minAge(0)
                .bookingCutoffMinutes(0).active(true).build();
        activity.getTypes().add(trekking);
        activity.addEquipment(casco, true);
        activityRepository.save(activity);
    }

    private static RequestPostProcessor as(Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "x@mail.com", role, "Administrador");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static RequestPostProcessor admin() {
        return as(Role.ADMIN);
    }

    private boolean activityTypeExists(String name) {
        return activityTypeRepository.findAll().stream().anyMatch(t -> t.getName().equals(name));
    }

    // ---------- Pasos 1 a 3: elegir la lista y consultarla ----------

    @Test
    void index_opensActivityTypes() throws Exception {
        mockMvc.perform(get("/admin/types").with(admin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/types/activity"));
    }

    @Test
    void list_showsEveryTypeWithItsUsage() throws Exception {
        mockMvc.perform(get("/admin/types/activity").with(admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-types"))
                .andExpect(content().string(containsString("Trekking")))
                .andExpect(content().string(containsString("Avistamiento de aves")))
                .andExpect(content().string(containsString("1 actividad")))
                .andExpect(content().string(containsString("Sin actividades")));
    }

    @Test
    void list_equipmentShowsEquipmentTypes() throws Exception {
        mockMvc.perform(get("/admin/types/equipment").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Casco")))
                .andExpect(content().string(containsString("Tipos de equipamiento")));
    }

    @Test
    void list_unknownCategory_returns404() throws Exception {
        mockMvc.perform(get("/admin/types/otra").with(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------- Alta ----------

    @Test
    void create_addsTheTypeActive() throws Exception {
        mockMvc.perform(post("/admin/types/activity").with(admin()).with(csrf()).param("name", "  Kayak   doble "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/types/activity"))
                .andExpect(flash().attribute("notice", containsString("Kayak doble")));

        assertThat(activityTypeRepository.findAll())
                .anySatisfy(t -> {
                    assertThat(t.getName()).isEqualTo("Kayak doble");
                    assertThat(t.isActive()).isTrue();
                });
    }

    @Test
    void create_duplicateNameIgnoringCase_marksTheField() throws Exception {
        mockMvc.perform(post("/admin/types/activity").with(admin()).with(csrf()).param("name", "trekking"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-types"))
                .andExpect(model().attributeHasFieldErrors("form", "name"))
                .andExpect(content().string(containsString("ya existe un tipo de actividad con ese nombre")));
    }

    @Test
    void create_emptyName_marksTheField() throws Exception {
        mockMvc.perform(post("/admin/types/equipment").with(admin()).with(csrf()).param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name"));
    }

    // ---------- Modificación ----------

    @Test
    void rename_changesTheName() throws Exception {
        mockMvc.perform(post("/admin/types/activity/{id}/rename", aves.getId()).with(admin()).with(csrf())
                        .param("name", "Observación de aves"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("notice", containsString("Observación de aves")));

        assertThat(activityTypeRepository.findById(aves.getId()).orElseThrow().getName())
                .isEqualTo("Observación de aves");
    }

    @Test
    void rename_toAnotherTypesName_isRejected() throws Exception {
        mockMvc.perform(post("/admin/types/activity/{id}/rename", aves.getId()).with(admin()).with(csrf())
                        .param("name", "TREKKING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("ya existe")));

        assertThat(activityTypeRepository.findById(aves.getId()).orElseThrow().getName())
                .isEqualTo("Avistamiento de aves");
    }

    // ---------- Eliminación (3a) ----------

    @Test
    void delete_unusedType_removesIt() throws Exception {
        mockMvc.perform(post("/admin/types/activity/{id}/delete", aves.getId()).with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("notice", containsString("Se eliminó")));

        assertThat(activityTypeExists("Avistamiento de aves")).isFalse();
    }

    @Test
    void delete_typeInUse_isPreventedAndOffersToDeactivate() throws Exception {
        mockMvc.perform(post("/admin/types/activity/{id}/delete", trekking.getId()).with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("inUse"));

        assertThat(activityTypeExists("Trekking")).isTrue();
    }

    @Test
    void delete_equipmentInUse_isPrevented() throws Exception {
        mockMvc.perform(post("/admin/types/equipment/{id}/delete", casco.getId()).with(admin()).with(csrf()))
                .andExpect(flash().attributeExists("inUse"));

        assertThat(equipmentTypeRepository.findById(casco.getId())).isPresent();
    }

    // ---------- Activar y desactivar ----------

    @Test
    void deactivate_hidesItFromNewActivitiesButKeepsIt() throws Exception {
        mockMvc.perform(post("/admin/types/activity/{id}/deactivate", trekking.getId()).with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("notice", containsString("quedó desactivado")));

        assertThat(activityTypeRepository.findById(trekking.getId()).orElseThrow().isActive()).isFalse();
        assertThat(activityTypeRepository.findByActiveTrueOrderByNameAsc())
                .extracting(ActivityType::getName).doesNotContain("Trekking");
    }

    @Test
    void activate_makesItAvailableAgain() throws Exception {
        trekking.setActive(false);

        mockMvc.perform(post("/admin/types/activity/{id}/activate", trekking.getId()).with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(activityTypeRepository.findById(trekking.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void unknownType_returns404() throws Exception {
        mockMvc.perform(post("/admin/types/activity/{id}/delete", 999_999).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- Quién puede ----------

    @Test
    void onlyAdmins_canManageTypes() throws Exception {
        mockMvc.perform(get("/admin/types/activity").with(as(Role.PROVIDER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/types/activity").with(as(Role.TOURIST)).with(csrf()).param("name", "Hackeo"))
                .andExpect(status().isForbidden());

        assertThat(activityTypeExists("Hackeo")).isFalse();
    }

    @Test
    void adminPanel_linksToTheTypes() throws Exception {
        mockMvc.perform(get("/admin").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/types")));
    }
}
