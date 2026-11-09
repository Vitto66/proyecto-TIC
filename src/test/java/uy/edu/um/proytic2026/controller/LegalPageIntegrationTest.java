package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Términos y condiciones y política de privacidad: públicas, se leen sin iniciar sesión. */
@SpringBootTest
class LegalPageIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Con los filtros de seguridad, para comprobar que las páginas no piden sesión
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void terms_arePublic() throws Exception {
        mockMvc.perform(get("/terminos"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal-terms"))
                .andExpect(content().string(containsString("Términos y condiciones de uso")))
                .andExpect(content().string(containsString("24 horas")));
    }

    @Test
    void privacy_isPublic() throws Exception {
        mockMvc.perform(get("/privacidad"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal-privacy"))
                .andExpect(content().string(containsString("Política de privacidad")))
                .andExpect(content().string(containsString("18.331")));
    }

    @Test
    void footer_linksToBothPages() throws Exception {
        mockMvc.perform(get("/terminos"))
                .andExpect(content().string(containsString("href=\"/privacidad\"")));
        mockMvc.perform(get("/privacidad"))
                .andExpect(content().string(containsString("href=\"/terminos\"")));
    }
}
