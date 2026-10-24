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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityEquipment;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.EquipmentType;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Role;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.EquipmentTypeRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CU-08 de punta a punta, contra H2 y con Spring Security activo: "Mis actividades", el
 * formulario, la publicación con tipos y equipamiento, las validaciones y las precondiciones.
 */
@SpringBootTest
@Transactional
class ActivityPageIntegrationTest {

    private static final String URL = "/provider/activities";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LocalityRepository localityRepository;

    @Autowired
    private ActivityTypeRepository activityTypeRepository;

    @Autowired
    private EquipmentTypeRepository equipmentTypeRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private Department rocha;
    private Locality laPaloma;
    private Locality puntaDelEste;
    private ActivityType kayak;
    private ActivityType naturaleza;
    private ActivityType inactivo;
    private EquipmentType chaleco;
    private EquipmentType gorro;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // En H2 estas tablas arrancan vacías; en Supabase ya tienen datos
        rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Department maldonado = departmentRepository.save(Department.builder().name("Maldonado").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        puntaDelEste = localityRepository.save(Locality.builder().department(maldonado).name("Punta del Este").build());

        kayak = activityTypeRepository.save(ActivityType.builder().name("Actividades acuáticas").build());
        naturaleza = activityTypeRepository.save(ActivityType.builder().name("Turismo de naturaleza").build());
        inactivo = activityTypeRepository.save(ActivityType.builder().name("Tipo dado de baja").active(false).build());
        chaleco = equipmentTypeRepository.save(EquipmentType.builder().name("Chaleco salvavidas").build());
        gorro = equipmentTypeRepository.save(EquipmentType.builder().name("Gorro").build());
    }

    // ---------- Datos y sesiones de prueba ----------

    private Provider provider(String email, String document, ProviderStatus status) {
        User user = userRepository.save(User.builder()
                .email(email).passwordHash(passwordEncoder.encode("secreta123")).build());
        Provider provider = providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.PHYSICAL).status(status)
                .firstName("Juan").lastName("Rodríguez").document(document).address("Ruta 10 km 264")
                .build());
        user.setProvider(provider);
        return provider;
    }

    /** Prestador aprobado con su emprendimiento en La Paloma: puede publicar. */
    private Provider providerWithBusiness() {
        Provider provider = provider("juan@mail.com", "45678901", ProviderStatus.APPROVED);
        businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("Salidas").address("Ruta 10").phone("099").email("a@b.uy")
                .build());
        return provider;
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Juan");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static RequestPostProcessor session(Provider provider) {
        return as(Role.PROVIDER, provider.getId());
    }

    /**
     * Un formulario válido. Los tests que prueban un dato malo lo reemplazan con set(): .param()
     * de MockMvc agrega un segundo valor en vez de reemplazar, y para un número Spring usaría el primero.
     */
    private MultiValueMap<String, String> validParams() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "Atardecer en kayak");
        params.add("description", "Recorrido por la laguna al atardecer.");
        params.add("departmentId", rocha.getId().toString());
        params.add("localityId", laPaloma.getId().toString());
        params.add("durationMinutes", "150");
        params.add("price", "1500");
        params.add("minAge", "12");
        params.add("bookingCutoffMinutes", "60");
        params.put("activityTypeIds", List.of(kayak.getId().toString(), naturaleza.getId().toString()));
        params.add("equipment[" + chaleco.getId() + "]", "PROVIDER");
        params.add("equipment[" + gorro.getId() + "]", "TOURIST");
        return params;
    }

    private MockHttpServletRequestBuilder publishRequest(Provider provider, MultiValueMap<String, String> params) {
        return post(URL).with(session(provider)).with(csrf()).params(params);
    }

    private MockHttpServletRequestBuilder validForm(Provider provider) {
        return publishRequest(provider, validParams());
    }

    // ---------- Paso 1: Mis actividades ----------

    @Test
    void list_withoutActivities_showsEmptyStateAndPublishButton() throws Exception {
        Provider provider = providerWithBusiness();

        mockMvc.perform(get(URL).with(session(provider)))
                .andExpect(status().isOk())
                .andExpect(view().name("activities"))
                .andExpect(content().string(containsString("Todavía no publicaste actividades")))
                .andExpect(content().string(containsString("/provider/activities/new")));
    }

    @Test
    void newForm_offersActiveTypesEquipmentAndBusinessLocation() throws Exception {
        Provider provider = providerWithBusiness();

        mockMvc.perform(get(URL + "/new").with(session(provider)))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-form"))
                .andExpect(content().string(containsString("Actividades acuáticas")))
                .andExpect(content().string(containsString("Chaleco salvavidas")))
                // Un tipo desactivado por el administrador no se ofrece
                .andExpect(content().string(not(containsString("Tipo dado de baja"))))
                // La ubicación arranca con la del emprendimiento
                .andExpect(model().attribute("form", hasProperty("localityId", is(laPaloma.getId()))));
    }

    // ---------- Pasos 2 a 7: publicar ----------

    @Test
    void publish_validData_savesActivityWithTypesAndEquipment() throws Exception {
        Provider provider = providerWithBusiness();

        mockMvc.perform(validForm(provider))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(URL))
                .andExpect(flash().attribute("published", "Atardecer en kayak"));

        List<Activity> saved = activityRepository.findAllByProviderId(provider.getId());
        assertThat(saved).hasSize(1);
        Activity activity = saved.get(0);
        assertThat(activity.isActive()).isTrue();
        assertThat(activity.getPrice()).isEqualByComparingTo("1500");
        assertThat(activity.getTypes()).extracting(ActivityType::getName)
                .containsExactlyInAnyOrder("Actividades acuáticas", "Turismo de naturaleza");
        assertThat(activity.getEquipment()).hasSize(2);
        assertThat(activity.getEquipment())
                .filteredOn(e -> e.getEquipmentType().getId().equals(chaleco.getId()))
                .singleElement().extracting(ActivityEquipment::isProvidedByProvider).isEqualTo(true);
        assertThat(activity.getEquipment())
                .filteredOn(e -> e.getEquipmentType().getId().equals(gorro.getId()))
                .singleElement().extracting(ActivityEquipment::isProvidedByProvider).isEqualTo(false);
    }

    @Test
    void publish_thenListShowsTheActivity() throws Exception {
        Provider provider = providerWithBusiness();
        mockMvc.perform(validForm(provider)).andExpect(status().is3xxRedirection());

        mockMvc.perform(get(URL).with(session(provider)).flashAttr("published", "Atardecer en kayak"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("quedó publicada")))
                .andExpect(content().string(containsString("Atardecer en kayak")))
                .andExpect(content().string(containsString("La Paloma, Rocha")))
                .andExpect(content().string(containsString("2 h 30 min")))
                .andExpect(content().string(containsString("$ 1.500")));
    }

    @Test
    void publish_withoutEquipment_isAllowed() throws Exception {
        Provider provider = providerWithBusiness();

        MultiValueMap<String, String> params = validParams();
        params.set("equipment[" + chaleco.getId() + "]", "");
        params.set("equipment[" + gorro.getId() + "]", "");

        mockMvc.perform(publishRequest(provider, params))
                .andExpect(status().is3xxRedirection());

        assertThat(activityRepository.findAllByProviderId(provider.getId()))
                .singleElement().satisfies(a -> assertThat(a.getEquipment()).isEmpty());
    }

    @Test
    void publish_withoutCsrfToken_isForbidden() throws Exception {
        Provider provider = providerWithBusiness();

        mockMvc.perform(post(URL).with(session(provider)).param("name", "Kayak"))
                .andExpect(status().isForbidden());
    }

    // ---------- 6a. Faltan datos obligatorios ----------

    @Test
    void publish_emptyForm_marksEveryRequiredField() throws Exception {
        Provider provider = providerWithBusiness();

        mockMvc.perform(post(URL).with(session(provider)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-form"))
                .andExpect(model().attributeHasFieldErrors("form", "name", "description", "departmentId",
                        "localityId", "durationMinutes", "price", "minAge", "bookingCutoffMinutes",
                        "activityTypeIds"));

        assertThat(activityRepository.count()).isZero();
    }

    @Test
    void publish_negativeOrNonNumericValues_areRejected() throws Exception {
        Provider provider = providerWithBusiness();

        MultiValueMap<String, String> params = validParams();
        params.set("price", "-10");
        params.set("durationMinutes", "0");
        params.set("minAge", "doce");

        mockMvc.perform(publishRequest(provider, params))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "price", "durationMinutes", "minAge"));

        assertThat(activityRepository.count()).isZero();
    }

    @Test
    void publish_inactiveType_isRejected() throws Exception {
        Provider provider = providerWithBusiness();

        MultiValueMap<String, String> params = validParams();
        params.set("activityTypeIds", inactivo.getId().toString());

        mockMvc.perform(publishRequest(provider, params))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "activityTypeIds"));
    }

    @Test
    void publish_localityFromAnotherDepartment_isRejected() throws Exception {
        Provider provider = providerWithBusiness();

        // Punta del Este es de Maldonado, no de Rocha
        MultiValueMap<String, String> params = validParams();
        params.set("localityId", puntaDelEste.getId().toString());

        mockMvc.perform(publishRequest(provider, params))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "localityId"));
    }

    // ---------- Precondiciones ----------

    @Test
    void approvedWithoutBusiness_isAskedToRegisterItFirst() throws Exception {
        Provider provider = provider("sin@mail.com", "22222222", ProviderStatus.APPROVED);

        mockMvc.perform(get(URL).with(session(provider)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Primero registrá tu emprendimiento")));

        mockMvc.perform(get(URL + "/new").with(session(provider)))
                .andExpect(redirectedUrl(URL));

        mockMvc.perform(validForm(provider))
                .andExpect(redirectedUrl(URL));
        assertThat(activityRepository.count()).isZero();
    }

    @Test
    void pendingProvider_cannotPublish() throws Exception {
        Provider provider = provider("pendiente@mail.com", "11111111", ProviderStatus.PENDING);

        mockMvc.perform(get(URL).with(session(provider)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no podés publicar actividades")));

        mockMvc.perform(validForm(provider)).andExpect(redirectedUrl(URL));
        assertThat(activityRepository.count()).isZero();
    }

    @Test
    void withoutSession_redirectsToLogin_andOtherRolesAreForbidden() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
        mockMvc.perform(get(URL).with(as(Role.TOURIST, 1L))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(as(Role.ADMIN, 1L))).andExpect(status().isForbidden());
    }

    // ---------- Acceso desde el espacio del prestador ----------

    @Test
    void providerSpace_withBusiness_linksToMyActivities() throws Exception {
        Provider provider = providerWithBusiness();

        mockMvc.perform(get("/provider").with(session(provider)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mis actividades")))
                .andExpect(content().string(containsString("/provider/activities")));
    }

    @Test
    void providerSpace_withoutBusiness_hasNoActivitiesLink() throws Exception {
        Provider provider = provider("sin@mail.com", "22222222", ProviderStatus.APPROVED);

        mockMvc.perform(get("/provider").with(session(provider)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/provider/activities"))));
    }
}
