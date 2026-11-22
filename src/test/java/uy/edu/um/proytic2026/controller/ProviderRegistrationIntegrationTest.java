package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-02 de punta a punta, contra la base H2 en memoria: la página del formulario. */
@SpringBootTest
@Transactional
class ProviderRegistrationIntegrationTest {

    private static final String PAGE_URL = "/register/provider";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private TouristRepository touristRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }


    // ---------- Página web (Thymeleaf) ----------

    private MockHttpServletRequestBuilder legalForm() {
        return legalForm("211234560018", "info@laguna.uy", "secreta123");
    }

    private MockHttpServletRequestBuilder legalForm(String rut, String email, String confirmPassword) {
        return legalFormWithoutTerms(rut, email, confirmPassword).param("acceptTerms", "true");
    }

    /** El formulario sin marcar la casilla de términos y privacidad. */
    private MockHttpServletRequestBuilder legalFormWithoutTerms(String rut, String email, String confirmPassword) {
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
    void page_withoutAcceptingTerms_marksFieldAndDoesNotCreateProvider() throws Exception {
        mockMvc.perform(legalFormWithoutTerms("211234560018", "info@laguna.uy", "secreta123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register-provider"))
                .andExpect(model().attributeHasFieldErrors("form", "acceptTerms"));

        assertThat(providerRepository.findByStatus(ProviderStatus.PENDING)).isEmpty();
    }

    @Test
    void page_rutAlreadyRegistered_marksRutField() throws Exception {
        mockMvc.perform(legalForm()).andExpect(status().is3xxRedirection());

        mockMvc.perform(legalForm("211234560018", "otro@laguna.uy", "secreta123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "rut"));
    }

    @Test
    void page_emailAlreadyProvider_offersLoginLink() throws Exception {
        mockMvc.perform(legalForm()).andExpect(status().is3xxRedirection());

        // Mismo correo, otro RUT: 5a. se informa y se ofrece iniciar sesión
        mockMvc.perform(legalForm("219999990013", "info@laguna.uy", "secreta123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "email"))
                .andExpect(content().string(containsString("Iniciá sesión con esa cuenta")))
                .andExpect(content().string(containsString("href=\"/login\"")));
    }

    @Test
    void page_existingTourist_getsProviderProfileOnSameAccount() throws Exception {
        // 5c. El correo ya es de un turista y la contraseña coincide: se agrega el perfil a la misma cuenta
        mockMvc.perform(post("/register/tourist")
                        .param("firstName", "Juan")
                        .param("lastName", "Rodríguez")
                        .param("nationalId", "45678901")
                        .param("phone", "")
                        .param("email", "info@laguna.uy")
                        .param("password", "secreta123")
                        .param("confirmPassword", "secreta123")
                        .param("acceptTerms", "true"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(legalForm()).andExpect(status().is3xxRedirection());

        Provider provider = providerRepository.findByStatus(ProviderStatus.PENDING).getFirst();
        assertThat(provider.getUser().getEmail()).isEqualTo("info@laguna.uy");
        assertThat(touristRepository.existsById(provider.getUser().getId())).isTrue();
    }
}
