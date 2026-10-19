package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.entity.Admin;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.AdminRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * CU-04 de punta a punta, contra la base H2 en memoria y con Spring Security activo:
 * el login, la elección de perfil, la redirección por rol y la protección de las rutas.
 */
@SpringBootTest
@Transactional
class LoginPageIntegrationTest {

    private static final String PASSWORD = "secreta123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TouristRepository touristRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // springSecurity() activa la cadena de filtros real: sesión, roles y CSRF
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---------- Cuentas de prueba ----------

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash(passwordEncoder.encode(PASSWORD)).build());
    }

    private void addTourist(User user, String nationalId) {
        user.setTourist(touristRepository.save(Tourist.builder()
                .user(user).firstName("Ana").lastName("Pérez").nationalId(nationalId).build()));
    }

    private void addProvider(User user, ProviderStatus status, String document) {
        user.setProvider(providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.PHYSICAL).status(status)
                .firstName("Juan").lastName("Rodríguez").document(document).address("Ruta 10 km 264")
                .build()));
    }

    private void addAdmin(User user) {
        user.setAdmin(adminRepository.save(Admin.builder().user(user).name("Admin Mnatural").build()));
    }

    private void touristAccount(String email) {
        addTourist(createUser(email), "12345678");
    }

    // ---------- Pedidos ----------

    private MockHttpServletRequestBuilder loginRequest(String email, String password) {
        return post("/login").with(csrf()).param("email", email).param("password", password);
    }

    /** Inicia sesión, comprueba a dónde redirige y devuelve la sesión para seguir navegando. */
    private MockHttpSession loginAndGetSession(String email, String expectedRedirect) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(email, PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedRedirect))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // ---------- Formulario ----------

    @Test
    void loginPage_showsForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Iniciar sesión")))
                .andExpect(content().string(containsString("Correo electrónico")));
    }

    @Test
    void login_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/login").param("email", "ana@mail.com").param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_emptyFields_showsFieldErrors() throws Exception {
        mockMvc.perform(loginRequest("", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeHasFieldErrors("form", "email", "password"));
    }

    // ---------- 2a. Credenciales incorrectas ----------

    @Test
    void login_wrongPassword_showsGenericError() throws Exception {
        touristAccount("ana@mail.com");

        mockMvc.perform(loginRequest("ana@mail.com", "otraClave99"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Correo o contraseña incorrectos")));
    }

    @Test
    void login_unknownEmail_showsTheSameGenericError() throws Exception {
        mockMvc.perform(loginRequest("nadie@mail.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Correo o contraseña incorrectos")));
    }

    // ---------- Ingreso por perfil ----------

    @Test
    void login_tourist_goesToTouristSpace() throws Exception {
        touristAccount("ana@mail.com");

        MockHttpSession session = loginAndGetSession("ana@mail.com", "/tourist");

        mockMvc.perform(get("/tourist").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("space"))
                .andExpect(content().string(containsString("Tu espacio de turista")))
                .andExpect(content().string(containsString("Ana")));
    }

    @Test
    void login_emailIsCaseInsensitive() throws Exception {
        touristAccount("ana@mail.com");

        loginAndGetSession("  ANA@Mail.com ", "/tourist");
    }

    @Test
    void login_pendingProvider_entersButSeesPendingNotice() throws Exception {
        addProvider(createUser("juan@mail.com"), ProviderStatus.PENDING, "45678901");

        MockHttpSession session = loginAndGetSession("juan@mail.com", "/provider");

        mockMvc.perform(get("/provider").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pendiente de aprobación")));
    }

    @Test
    void login_approvedProvider_doesNotSeePendingNotice() throws Exception {
        addProvider(createUser("juan@mail.com"), ProviderStatus.APPROVED, "45678901");

        MockHttpSession session = loginAndGetSession("juan@mail.com", "/provider");

        mockMvc.perform(get("/provider").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("pendiente de aprobación"))));
    }

    @Test
    void login_admin_goesToAdminPanel() throws Exception {
        addAdmin(createUser("admin@mail.com"));

        MockHttpSession session = loginAndGetSession("admin@mail.com", "/admin");

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel de administración")));
    }

    // ---------- 2b. Prestador rechazado ----------

    @Test
    void login_rejectedProvider_isBlocked() throws Exception {
        addProvider(createUser("juan@mail.com"), ProviderStatus.REJECTED, "45678901");

        mockMvc.perform(loginRequest("juan@mail.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("rechazada")));

        // Y no quedó ninguna sesión iniciada
        mockMvc.perform(get("/provider"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    // ---------- 2c. Un correo con más de un perfil ----------

    private User touristAndApprovedProvider(String email) {
        User user = createUser(email);
        addTourist(user, "12345678");
        addProvider(user, ProviderStatus.APPROVED, "45678901");
        return user;
    }

    @Test
    void login_touristAndProvider_asksWhichProfileToUse() throws Exception {
        touristAndApprovedProvider("ana@mail.com");

        MockHttpSession session = loginAndGetSession("ana@mail.com", "/login/profile");

        mockMvc.perform(get("/login/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("login-profile"))
                .andExpect(content().string(containsString("Turista")))
                .andExpect(content().string(containsString("Prestador")));

        // Todavía no entró: sin elegir perfil no puede ver ningún espacio
        mockMvc.perform(get("/tourist").session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void chooseProvider_entersProviderSpace_andCannotUseTouristSpace() throws Exception {
        touristAndApprovedProvider("ana@mail.com");
        MockHttpSession session = loginAndGetSession("ana@mail.com", "/login/profile");

        mockMvc.perform(post("/login/profile").session(session).with(csrf()).param("role", "PROVIDER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"));

        mockMvc.perform(get("/provider").session(session)).andExpect(status().isOk());
        // Un prestador no puede acceder a las funciones del turista (RF-02)
        mockMvc.perform(get("/tourist").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void chooseTourist_entersTouristSpace_andCannotUseProviderSpace() throws Exception {
        touristAndApprovedProvider("ana@mail.com");
        MockHttpSession session = loginAndGetSession("ana@mail.com", "/login/profile");

        mockMvc.perform(post("/login/profile").session(session).with(csrf()).param("role", "TOURIST"))
                .andExpect(redirectedUrl("/tourist"));

        mockMvc.perform(get("/tourist").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/provider").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void chooseProfile_roleNotOffered_showsErrorAndDoesNotLogIn() throws Exception {
        touristAndApprovedProvider("ana@mail.com");
        MockHttpSession session = loginAndGetSession("ana@mail.com", "/login/profile");

        mockMvc.perform(post("/login/profile").session(session).with(csrf()).param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(view().name("login-profile"))
                .andExpect(content().string(containsString("Elegí con qué perfil")));

        mockMvc.perform(get("/admin").session(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void chooseProfile_withoutPasswordStep_goesBackToLogin() throws Exception {
        mockMvc.perform(get("/login/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/login/profile").with(csrf()).param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---------- Protección de rutas ----------

    @Test
    void protectedPages_withoutSession_redirectToLogin() throws Exception {
        for (String path : new String[]{"/tourist", "/provider", "/admin"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", endsWith("/login")));
        }
    }

    @Test
    void tourist_cannotEnterAdminOrProviderSpace() throws Exception {
        touristAccount("ana@mail.com");
        MockHttpSession session = loginAndGetSession("ana@mail.com", "/tourist");

        mockMvc.perform(get("/admin").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/provider").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void loginPage_whenAlreadyLoggedIn_goesToTheUsersSpace() throws Exception {
        touristAccount("ana@mail.com");
        MockHttpSession session = loginAndGetSession("ana@mail.com", "/tourist");

        mockMvc.perform(get("/login").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tourist"));
    }

    // ---------- Cerrar sesión ----------

    @Test
    void logout_endsTheSession() throws Exception {
        touristAccount("ana@mail.com");
        MockHttpSession session = loginAndGetSession("ana@mail.com", "/tourist");

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?logout"));

        mockMvc.perform(get("/tourist").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    // ---------- Menú inicial ----------

    @Test
    void home_isPublic_andOffersLoginAndRegistration() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("Descubrí Uruguay al natural")))
                .andExpect(content().string(containsString("Iniciar sesión")))
                .andExpect(content().string(containsString("/register/tourist")))
                .andExpect(content().string(containsString("/register/provider")))
                .andExpect(content().string(not(containsString("Mi espacio"))));
    }

    @Test
    void home_withSession_offersMySpaceAndLogout() throws Exception {
        touristAccount("ana@mail.com");
        MockHttpSession session = loginAndGetSession("ana@mail.com", "/tourist");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi espacio")))
                .andExpect(content().string(containsString("Salir")));
    }

    @Test
    void home_afterLogout_showsGoodbyeNotice() throws Exception {
        mockMvc.perform(get("/").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cerraste sesión")));
    }

    // ---------- Lo que ya existía sigue siendo público ----------

    @Test
    void registrationPages_stayPublic() throws Exception {
        mockMvc.perform(get("/register/tourist")).andExpect(status().isOk());
        mockMvc.perform(get("/register/provider")).andExpect(status().isOk());
    }

    @Test
    void api_registration_worksWithoutSessionOrCsrfToken() throws Exception {
        // La API la consumen otras plataformas (RF-10): ni sesión ni token CSRF
        mockMvc.perform(post("/api/auth/register/tourist").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ana","lastName":"Pérez","nationalId":"55555555",
                                 "email":"api@mail.com","password":"secreta123"}
                                """))
                .andExpect(status().isCreated());
    }
}
