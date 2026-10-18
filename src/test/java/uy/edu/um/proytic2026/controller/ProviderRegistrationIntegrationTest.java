package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.repository.ProviderRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-02 de punta a punta, contra la base H2 en memoria: la API REST y la página del formulario. */
@SpringBootTest
@Transactional
class ProviderRegistrationIntegrationTest {

    private static final String API_URL = "/api/auth/register/provider";
    private static final String PAGE_URL = "/register/provider";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProviderRepository providerRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String physicalJson(String email, String document) {
        return """
                {"personType":"PHYSICAL","firstName":"Juan","lastName":"Rodríguez",
                 "document":"%s","address":"Ruta 10 km 264","email":"%s","password":"secreta123"}
                """.formatted(document, email);
    }

    // ---------- API REST ----------

    @Test
    void api_validPhysicalPerson_returns201Pending() throws Exception {
        mockMvc.perform(post(API_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(physicalJson("juan@mail.com", "45678901")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("PROVIDER"))
                .andExpect(jsonPath("$.providerStatus").value("PENDING"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void api_sameEmailTwice_returns409() throws Exception {
        mockMvc.perform(post(API_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(physicalJson("juan@mail.com", "45678901")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(API_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(physicalJson("juan@mail.com", "11111111")))
                .andExpect(status().isConflict());
    }

    @Test
    void api_legalPersonWithBadRut_returns400WithFields() throws Exception {
        String body = """
                {"personType":"LEGAL","rut":"123","address":"Av. Costanera 123",
                 "email":"info@laguna.uy","password":"secreta123"}
                """;
        mockMvc.perform(post(API_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.rut").exists())
                .andExpect(jsonPath("$.fields.legalName").exists())
                .andExpect(jsonPath("$.fields.businessName").exists());
    }

    @Test
    void api_existingTourist_getsProviderProfileOnSameAccount() throws Exception {
        mockMvc.perform(post("/api/auth/register/tourist").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Juan","lastName":"Rodríguez","nationalId":"45678901",
                                 "email":"juan@mail.com","password":"secreta123"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post(API_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(physicalJson("juan@mail.com", "45678901")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("TOURIST"))
                .andExpect(jsonPath("$.roles[1]").value("PROVIDER"));
    }

    // ---------- Página web (Thymeleaf) ----------

    private MockHttpServletRequestBuilder legalForm() {
        return legalForm("211234560018", "info@laguna.uy", "secreta123");
    }

    private MockHttpServletRequestBuilder legalForm(String rut, String email, String confirmPassword) {
        return post(PAGE_URL)
                .param("personType", "LEGAL")
                .param("rut", rut)
                .param("legalName", "Laguna Aventuras S.A.")
                .param("businessName", "Kayak Laguna de Rocha")
                .param("address", "Av. Costanera 123, Rocha")
                .param("email", email)
                .param("password", "secreta123")
                .param("confirmPassword", confirmPassword);
    }

    @Test
    void page_showsForm() throws Exception {
        mockMvc.perform(get(PAGE_URL))
                .andExpect(status().isOk())
                .andExpect(view().name("register-provider"))
                .andExpect(content().string(containsString("Registrarse como prestador")))
                .andExpect(content().string(containsString("Persona jurídica")));
    }

    @Test
    void page_validSubmit_redirectsToConfirmationAndSavesPendingProvider() throws Exception {
        mockMvc.perform(legalForm())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register/provider/sent"));

        assertThat(providerRepository.findByStatus(ProviderStatus.PENDING))
                .extracting(Provider::getRut).containsExactly("211234560018");
    }

    @Test
    void page_confirmation_showsPendingMessage() throws Exception {
        mockMvc.perform(get(PAGE_URL + "/sent"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pendiente de aprobación por un administrador")));
    }

    @Test
    void page_invalidSubmit_showsFormAgainWithFieldErrors() throws Exception {
        mockMvc.perform(legalForm("123", "info@laguna.uy", "otra"))
                .andExpect(status().isOk())
                .andExpect(view().name("register-provider"))
                .andExpect(model().attributeHasFieldErrors("form", "rut", "confirmPassword"));
    }

    @Test
    void page_rutAlreadyRegistered_marksRutField() throws Exception {
        mockMvc.perform(legalForm()).andExpect(status().is3xxRedirection());

        mockMvc.perform(legalForm("211234560018", "otro@laguna.uy", "secreta123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "rut"));
    }
}
