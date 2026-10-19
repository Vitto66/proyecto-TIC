package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Role;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

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
 * CU-03 de punta a punta, contra H2 y con Spring Security activo: la lista de pendientes,
 * el detalle, aprobar, rechazar y quién puede hacerlo.
 */
@SpringBootTest
@Transactional
class AdminProviderIntegrationTest {

    private static final String PASSWORD = "secreta123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---------- Datos y sesiones de prueba ----------

    private Provider physicalProvider(String email, String document, ProviderStatus status) {
        User user = userRepository.save(User.builder()
                .email(email).passwordHash(passwordEncoder.encode(PASSWORD)).build());
        Provider provider = providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.PHYSICAL).status(status)
                .firstName("Juan").lastName("Rodríguez").document(document).address("Ruta 10 km 264")
                .build());
        user.setProvider(provider);
        return provider;
    }

    private Provider legalProvider(String email, String rut) {
        User user = userRepository.save(User.builder()
                .email(email).passwordHash(passwordEncoder.encode(PASSWORD)).build());
        Provider provider = providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.LEGAL).status(ProviderStatus.PENDING)
                .rut(rut).legalName("Laguna Aventuras S.A.").businessName("Kayak Laguna de Rocha")
                .address("Av. Costanera 123, Rocha")
                .build());
        user.setProvider(provider);
        return provider;
    }

    /** Sesión iniciada con el perfil indicado, igual a la que arma LoginPageController. */
    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "Admin Mnatural");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static RequestPostProcessor admin() {
        return as(Role.ADMIN, 1L);
    }

    private ProviderStatus statusOf(Provider provider) {
        return providerRepository.findById(provider.getId()).orElseThrow().getStatus();
    }

    // ---------- Pasos 1 y 2: lista y detalle ----------

    @Test
    void list_showsOnlyPendingProviders() throws Exception {
        legalProvider("info@laguna.uy", "211234560018");
        physicalProvider("aprobado@mail.com", "11111111", ProviderStatus.APPROVED);

        mockMvc.perform(get("/admin/providers").with(admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-providers"))
                .andExpect(content().string(containsString("Laguna Aventuras S.A.")))
                .andExpect(content().string(containsString("211234560018")))
                .andExpect(content().string(not(containsString("aprobado@mail.com"))));
    }

    @Test
    void list_withoutPendingProviders_saysThereIsNothingToReview() throws Exception {
        mockMvc.perform(get("/admin/providers").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No hay solicitudes para revisar")));
    }

    @Test
    void detail_showsTheProviderData() throws Exception {
        Provider provider = physicalProvider("juan@mail.com", "45678901", ProviderStatus.PENDING);

        mockMvc.perform(get("/admin/providers/" + provider.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-provider-detail"))
                .andExpect(content().string(containsString("45678901")))
                .andExpect(content().string(containsString("Ruta 10 km 264")))
                .andExpect(content().string(containsString("juan@mail.com")))
                .andExpect(content().string(containsString("Aprobar prestador")));
    }

    @Test
    void detail_unknownProvider_returns404() throws Exception {
        mockMvc.perform(get("/admin/providers/999999").with(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------- Pasos 3 y 4: aprobar o rechazar ----------

    @Test
    void approve_pendingProvider_becomesApprovedAndReturnsToList() throws Exception {
        Provider provider = physicalProvider("juan@mail.com", "45678901", ProviderStatus.PENDING);

        mockMvc.perform(post("/admin/providers/" + provider.getId() + "/approve").with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/providers"))
                .andExpect(flash().attribute("notice", containsString("aprobado")));

        assertThat(statusOf(provider)).isEqualTo(ProviderStatus.APPROVED);
    }

    @Test
    void approvedProvider_seesApprovalNoticeInTheirSpace() throws Exception {
        Provider provider = physicalProvider("juan@mail.com", "45678901", ProviderStatus.PENDING);
        mockMvc.perform(post("/admin/providers/" + provider.getId() + "/approve").with(admin()).with(csrf()));

        mockMvc.perform(get("/provider").with(as(Role.PROVIDER, provider.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fue aprobada")))
                .andExpect(content().string(not(containsString("pendiente de aprobación"))));
    }

    @Test
    void reject_pendingProvider_becomesRejectedAndCannotLogInAsProvider() throws Exception {
        Provider provider = physicalProvider("juan@mail.com", "45678901", ProviderStatus.PENDING);

        mockMvc.perform(post("/admin/providers/" + provider.getId() + "/reject").with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/providers"))
                .andExpect(flash().attribute("notice", containsString("rechazado")));

        assertThat(statusOf(provider)).isEqualTo(ProviderStatus.REJECTED);

        // 3a. El prestador rechazado no puede ingresar a su espacio (CU-04 2b)
        mockMvc.perform(post("/login").with(csrf()).param("email", "juan@mail.com").param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("rechazada")));
    }

    @Test
    void approve_alreadyReviewedProvider_showsErrorAndKeepsStatus() throws Exception {
        Provider provider = physicalProvider("juan@mail.com", "45678901", ProviderStatus.REJECTED);

        mockMvc.perform(post("/admin/providers/" + provider.getId() + "/approve").with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertThat(statusOf(provider)).isEqualTo(ProviderStatus.REJECTED);
    }

    @Test
    void reviewedProvider_detailHasNoDecisionButtons() throws Exception {
        Provider provider = physicalProvider("juan@mail.com", "45678901", ProviderStatus.APPROVED);

        mockMvc.perform(get("/admin/providers/" + provider.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ya fue revisada")))
                .andExpect(content().string(not(containsString("Aprobar prestador"))));
    }

    // ---------- Panel ----------

    @Test
    void adminPanel_showsPendingCountWithLinkToReview() throws Exception {
        physicalProvider("juan@mail.com", "45678901", ProviderStatus.PENDING);

        mockMvc.perform(get("/admin").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prestadores pendientes")))
                .andExpect(content().string(containsString("/admin/providers")));
    }

    // ---------- Permisos ----------

    @Test
    void touristOrProvider_cannotReviewProviders() throws Exception {
        Provider provider = physicalProvider("juan@mail.com", "45678901", ProviderStatus.PENDING);

        mockMvc.perform(get("/admin/providers").with(as(Role.TOURIST, 5L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/providers/" + provider.getId() + "/approve")
                        .with(as(Role.PROVIDER, provider.getId())).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(statusOf(provider)).isEqualTo(ProviderStatus.PENDING);
    }

    @Test
    void withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/providers"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    void approve_withoutCsrfToken_isForbidden() throws Exception {
        Provider provider = physicalProvider("juan@mail.com", "45678901", ProviderStatus.PENDING);

        mockMvc.perform(post("/admin/providers/" + provider.getId() + "/approve").with(admin()))
                .andExpect(status().isForbidden());

        assertThat(statusOf(provider)).isEqualTo(ProviderStatus.PENDING);
    }
}
