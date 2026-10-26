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
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Role;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
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
 * CU-07 de punta a punta, contra H2 y con Spring Security activo: el formulario, el registro,
 * las validaciones, el emprendimiento ya registrado (1a) y quién puede entrar.
 */
@SpringBootTest
@Transactional
class BusinessPageIntegrationTest {

    private static final String URL = "/provider/business";

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
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private Department rocha;
    private Locality laPaloma;
    private Locality puntaDelEste;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // En H2 las tablas de ubicación arrancan vacías; en Supabase ya tienen datos
        rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Department maldonado = departmentRepository.save(Department.builder().name("Maldonado").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        puntaDelEste = localityRepository.save(Locality.builder().department(maldonado).name("Punta del Este").build());
    }

    // ---------- Datos y sesiones de prueba ----------

    private Provider physicalProvider(String email, String document, ProviderStatus status) {
        User user = userRepository.save(User.builder()
                .email(email).phone("099111222").passwordHash(passwordEncoder.encode("secreta123")).build());
        Provider provider = providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.PHYSICAL).status(status)
                .firstName("Juan").lastName("Rodríguez").document(document).address("Ruta 10 km 264")
                .build());
        user.setProvider(provider);
        return provider;
    }

    private Provider legalApprovedProvider() {
        User user = userRepository.save(User.builder()
                .email("info@laguna.uy").passwordHash(passwordEncoder.encode("secreta123")).build());
        Provider provider = providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.LEGAL).status(ProviderStatus.APPROVED)
                .rut("211234560018").legalName("Laguna Aventuras S.A.").businessName("Kayak Laguna de Rocha")
                .address("Av. Costanera 123, Rocha")
                .build());
        user.setProvider(provider);
        return provider;
    }

    private Provider approvedProvider() {
        return physicalProvider("juan@mail.com", "45678901", ProviderStatus.APPROVED);
    }

    /** Sesión iniciada con el perfil indicado, igual a la que arma LoginPageController. */
    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Juan");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static RequestPostProcessor providerSession(Provider provider) {
        return as(Role.PROVIDER, provider.getId());
    }

    private MockHttpServletRequestBuilder businessForm(Provider provider, Integer departmentId, Integer localityId) {
        return post(URL).with(providerSession(provider)).with(csrf())
                .param("name", "Kayak Laguna de Rocha")
                .param("departmentId", departmentId == null ? "" : departmentId.toString())
                .param("localityId", localityId == null ? "" : localityId.toString())
                .param("address", "Ruta 10 km 264")
                .param("phone", "099123456")
                .param("email", "info@laguna.uy")
                .param("description", "Salidas en kayak por la laguna.");
    }

    private MockHttpServletRequestBuilder validForm(Provider provider) {
        return businessForm(provider, rocha.getId(), laPaloma.getId());
    }

    // ---------- Formulario (paso 2) ----------

    @Test
    void form_approvedProvider_showsFormWithLocations() throws Exception {
        Provider provider = approvedProvider();

        mockMvc.perform(get(URL).with(providerSession(provider)))
                .andExpect(status().isOk())
                .andExpect(view().name("business-form"))
                .andExpect(content().string(containsString("Registrar mi emprendimiento")))
                .andExpect(content().string(containsString("Rocha")))
                .andExpect(content().string(containsString("La Paloma")))
                // El correo de contacto arranca con el de la cuenta
                .andExpect(content().string(containsString("juan@mail.com")));
    }

    @Test
    void form_legalPerson_prefillsBusinessName() throws Exception {
        Provider provider = legalApprovedProvider();

        mockMvc.perform(get(URL).with(providerSession(provider)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("form", hasProperty("name", is("Kayak Laguna de Rocha"))));
    }

    // ---------- Registro (pasos 3 y 4) ----------

    @Test
    void submit_validData_savesBusinessAndShowsIt() throws Exception {
        Provider provider = approvedProvider();

        mockMvc.perform(validForm(provider))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(URL))
                .andExpect(flash().attribute("created", true));

        Business saved = businessRepository.findByProviderId(provider.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Kayak Laguna de Rocha");
        assertThat(saved.getLocality().getId()).isEqualTo(laPaloma.getId());

        mockMvc.perform(get(URL).with(providerSession(provider)).flashAttr("created", true))
                .andExpect(status().isOk())
                .andExpect(view().name("business"))
                .andExpect(content().string(containsString("quedó registrado")))
                .andExpect(content().string(containsString("La Paloma, Rocha")));
    }

    @Test
    void submit_withoutCsrfToken_isForbidden() throws Exception {
        Provider provider = approvedProvider();

        mockMvc.perform(post(URL).with(providerSession(provider)).param("name", "Kayak"))
                .andExpect(status().isForbidden());
    }

    // ---------- 3a. Faltan datos obligatorios ----------

    @Test
    void submit_emptyForm_marksEveryRequiredField() throws Exception {
        Provider provider = approvedProvider();

        mockMvc.perform(post(URL).with(providerSession(provider)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("business-form"))
                .andExpect(model().attributeHasFieldErrors("form",
                        "name", "departmentId", "localityId", "address", "phone", "email", "description"));

        assertThat(businessRepository.count()).isZero();
    }

    @Test
    void submit_invalidEmail_marksEmailField() throws Exception {
        Provider provider = approvedProvider();

        mockMvc.perform(validForm(provider).param("email", "no-es-correo"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    @Test
    void submit_localityFromAnotherDepartment_marksLocalityField() throws Exception {
        Provider provider = approvedProvider();

        // Punta del Este es de Maldonado, no de Rocha
        mockMvc.perform(businessForm(provider, rocha.getId(), puntaDelEste.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("business-form"))
                .andExpect(model().attributeHasFieldErrors("form", "localityId"));

        assertThat(businessRepository.count()).isZero();
    }

    // ---------- 1a. Ya tiene su emprendimiento registrado ----------

    @Test
    void alreadyRegistered_pageShowsNoticeInsteadOfForm() throws Exception {
        Provider provider = approvedProvider();
        mockMvc.perform(validForm(provider)).andExpect(status().is3xxRedirection());

        mockMvc.perform(get(URL).with(providerSession(provider)))
                .andExpect(status().isOk())
                .andExpect(view().name("business"))
                .andExpect(content().string(containsString("ya está registrado")))
                .andExpect(content().string(not(containsString("Registrar emprendimiento"))));
    }

    @Test
    void alreadyRegistered_secondSubmitDoesNotCreateAnother() throws Exception {
        Provider provider = approvedProvider();
        mockMvc.perform(validForm(provider)).andExpect(status().is3xxRedirection());

        mockMvc.perform(validForm(provider))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(URL));

        assertThat(businessRepository.count()).isEqualTo(1);
    }

    // ---------- Precondición: prestador aprobado ----------

    @Test
    void pendingProvider_cannotSeeFormNorRegister() throws Exception {
        Provider provider = physicalProvider("pendiente@mail.com", "11111111", ProviderStatus.PENDING);

        mockMvc.perform(get(URL).with(providerSession(provider)))
                .andExpect(status().isOk())
                .andExpect(view().name("business"))
                .andExpect(content().string(containsString("tiene que estar aprobada")));

        mockMvc.perform(validForm(provider))
                .andExpect(status().isOk())
                .andExpect(view().name("business"));

        assertThat(businessRepository.count()).isZero();
    }

    // ---------- Quién puede entrar ----------

    @Test
    void withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    void touristAndAdmin_areForbidden() throws Exception {
        mockMvc.perform(get(URL).with(as(Role.TOURIST, 1L))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(as(Role.ADMIN, 1L))).andExpect(status().isForbidden());
    }

    // ---------- Acceso desde el espacio del prestador ----------

    @Test
    void providerSpace_approvedWithoutBusiness_offersToRegisterIt() throws Exception {
        Provider provider = approvedProvider();

        mockMvc.perform(get("/provider").with(providerSession(provider)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Registrar mi emprendimiento")))
                .andExpect(content().string(containsString("/provider/business")));
    }

    @Test
    void providerSpace_withBusiness_offersToSeeIt() throws Exception {
        Provider provider = approvedProvider();
        mockMvc.perform(validForm(provider)).andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/provider").with(providerSession(provider)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi emprendimiento")))
                .andExpect(content().string(not(containsString("Ya podés registrar tu emprendimiento"))));
    }

    @Test
    void providerSpace_pending_hasNoBusinessAccess() throws Exception {
        Provider provider = physicalProvider("pendiente@mail.com", "11111111", ProviderStatus.PENDING);

        mockMvc.perform(get("/provider").with(providerSession(provider)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/provider/business"))));
    }
}
