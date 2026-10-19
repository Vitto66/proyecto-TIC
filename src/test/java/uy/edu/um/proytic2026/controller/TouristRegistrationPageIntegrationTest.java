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
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-01 de punta a punta desde la página del formulario, contra la base H2 en memoria. */
@SpringBootTest
@Transactional
class TouristRegistrationPageIntegrationTest {

    private static final String PAGE_URL = "/register/tourist";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TouristRepository touristRepository;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private MockHttpServletRequestBuilder touristForm(String email, String nationalId,
                                                      String password, String confirmPassword) {
        return post(PAGE_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("firstName", "Ana")
                .param("lastName", "Pérez")
                .param("nationalId", nationalId)
                .param("phone", "")
                .param("email", email)
                .param("password", password)
                .param("confirmPassword", confirmPassword);
    }

    private MockHttpServletRequestBuilder touristForm() {
        return touristForm("ana@mail.com", "12345678", "secreta123", "secreta123");
    }

    @Test
    void page_showsForm() throws Exception {
        mockMvc.perform(get(PAGE_URL))
                .andExpect(status().isOk())
                .andExpect(view().name("register-tourist"))
                .andExpect(content().string(containsString("Crear cuenta de turista")))
                .andExpect(content().string(containsString("Cédula")));
    }

    @Test
    void page_validSubmit_redirectsAndSavesTourist() throws Exception {
        mockMvc.perform(touristForm())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register/tourist/done"));

        assertThat(touristRepository.findAll())
                .extracting(Tourist::getNationalId).containsExactly("12345678");
        // El teléfono vacío del formulario se guarda como null, no como ""
        assertThat(userRepository.findByEmailIgnoreCase("ana@mail.com"))
                .hasValueSatisfying(u -> assertThat(u.getPhone()).isNull());
    }

    @Test
    void page_done_showsAccountReadyMessage() throws Exception {
        mockMvc.perform(get(PAGE_URL + "/done"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tu cuenta está lista")));
    }

    @Test
    void page_invalidSubmit_showsFormAgainWithFieldErrors() throws Exception {
        mockMvc.perform(touristForm("no-es-correo", "123", "secreta123", "otra"))
                .andExpect(status().isOk())
                .andExpect(view().name("register-tourist"))
                .andExpect(model().attributeHasFieldErrors("form", "email", "nationalId", "confirmPassword"));

        assertThat(touristRepository.count()).isZero();
    }

    @Test
    void page_emailAlreadyTourist_marksEmailField() throws Exception {
        mockMvc.perform(touristForm()).andExpect(status().is3xxRedirection());

        mockMvc.perform(touristForm("ana@mail.com", "87654321", "secreta123", "secreta123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "email"))
                .andExpect(content().string(containsString("Iniciá sesión")));
    }

    @Test
    void page_nationalIdAlreadyRegistered_marksNationalIdField() throws Exception {
        mockMvc.perform(touristForm()).andExpect(status().is3xxRedirection());

        mockMvc.perform(touristForm("otra@mail.com", "12345678", "secreta123", "secreta123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "nationalId"));
    }

    @Test
    void api_confirmPasswordMismatch_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register/tourist").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ana","lastName":"Pérez","nationalId":"12345678",
                                 "email":"ana@mail.com","password":"secreta123","confirmPassword":"otra"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.confirmPassword").exists());
    }
}
