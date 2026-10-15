package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba el endpoint de registro de punta a punta, contra la base H2 en memoria. */
@SpringBootTest
@Transactional
class AuthControllerIntegrationTest {

    private static final String URL = "/api/auth/register/tourist";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String body(String email, String nationalId, String password) {
        return """
                {"firstName":"Ana","lastName":"Pérez","nationalId":"%s",
                 "email":"%s","password":"%s"}
                """.formatted(nationalId, email, password);
    }

    @Test
    void register_validData_returns201WithoutPassword() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ana@mail.com", "12345678", "secreta123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.roles[0]").value("TOURIST"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_sameEmailTwice_returns409() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ana@mail.com", "12345678", "secreta123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ana@mail.com", "87654321", "secreta123")))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidData_returns400WithFields() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("no-es-un-correo", "123", "corta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.nationalId").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
