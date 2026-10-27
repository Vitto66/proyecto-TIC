package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API para otras plataformas (RF-10) de punta a punta, contra H2 y con Spring Security activo:
 * se consulta sin sesión.
 */
@SpringBootTest
@Transactional
class PublicApiIntegrationTest {

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

    private MockMvc mockMvc;

    private Locality laPaloma;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // En H2 las tablas de ubicación arrancan vacías; en Supabase ya tienen datos
        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
    }

    private void business(String name, String email, String document, ProviderStatus status) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.PHYSICAL).status(status)
                .firstName("Juan").lastName("Rodríguez").document(document).address("Ruta 10 km 264")
                .build());
        businessRepository.save(Business.builder().provider(provider).locality(laPaloma).name(name)
                .description("Salidas guiadas").phone("099123456").email(email).build());
    }

    // ---------- CU-19 Listar emprendimientos ----------

    @Test
    void listBusinesses_withoutSession_returnsOnlyApprovedWithPublicData() throws Exception {
        business("Kayak Laguna de Rocha", "kayak@mail.com", "45678901", ProviderStatus.APPROVED);
        business("Cabalgatas del Este", "caballos@mail.com", "12345678", ProviderStatus.APPROVED);
        business("Pendiente S.A.", "pendiente@mail.com", "11111111", ProviderStatus.PENDING);
        business("Rechazado S.A.", "rechazado@mail.com", "22222222", ProviderStatus.REJECTED);

        mockMvc.perform(get("/api/businesses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // Ordenados por nombre
                .andExpect(jsonPath("$[0].name").value("Cabalgatas del Este"))
                .andExpect(jsonPath("$[1].name").value("Kayak Laguna de Rocha"))
                .andExpect(jsonPath("$[1].locality").value("La Paloma"))
                .andExpect(jsonPath("$[1].department").value("Rocha"))
                // Sin datos del prestador
                .andExpect(jsonPath("$[1].document").doesNotExist())
                .andExpect(jsonPath("$[1].provider").doesNotExist());
    }

    @Test
    void listBusinesses_noneRegistered_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/businesses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
