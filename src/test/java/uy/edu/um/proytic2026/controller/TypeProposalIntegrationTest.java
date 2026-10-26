package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CU-12 (el prestador propone un tipo) y CU-13 2a (el administrador lo acepta o lo rechaza),
 * de punta a punta contra H2 y con Spring Security activo.
 */
@SpringBootTest
@Transactional
class TypeProposalIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private AdminRepository adminRepository;
    @Autowired private ActivityTypeRepository activityTypeRepository;
    @Autowired private EquipmentTypeRepository equipmentTypeRepository;
    @Autowired private TypeProposalRepository proposalRepository;

    private MockMvc mockMvc;
    private Provider approved;
    private Admin admin;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        approved = provider("juan@mail.com", "45678901", ProviderStatus.APPROVED);
        User adminUser = userRepository.save(User.builder().email("jefa@mnatural.uy").passwordHash("x").build());
        admin = adminRepository.save(Admin.builder().user(adminUser).name("Jefa").build());
        activityTypeRepository.save(ActivityType.builder().name("Trekking").build());
        equipmentTypeRepository.save(EquipmentType.builder().name("Casco").active(false).build());
    }

    private Provider provider(String email, String document, ProviderStatus status) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        return providerRepository.save(Provider.builder().user(user).personType(PersonType.PHYSICAL)
                .status(status).firstName("Juan").lastName("Rodríguez").document(document).address("x").build());
    }

    private static RequestPostProcessor as(Role role, Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "x@mail.com", role, "X");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private MockHttpServletRequestBuilder propose(String category, String name) {
        return post("/provider/proposals").with(as(Role.PROVIDER, approved.getId())).with(csrf())
                .param("category", category).param("name", name)
                .param("description", "Salidas en Rocha para ver ballenas francas.");
    }

    private TypeProposal onlyProposal() {
        List<TypeProposal> all = proposalRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    // ---------- CU-12 pasos 1 a 3 ----------

    @Test
    void page_showsTheForm() throws Exception {
        mockMvc.perform(get("/provider/proposals").with(as(Role.PROVIDER, approved.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("provider-proposals"))
                .andExpect(content().string(containsString("Enviar propuesta")));
    }

    @Test
    void propose_savesItPendingAndShowsItInMyProposals() throws Exception {
        mockMvc.perform(propose("ACTIVITY", "  Avistamiento   de ballenas "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/proposals"))
                .andExpect(flash().attribute("notice", containsString("Avistamiento de ballenas")));

        TypeProposal p = onlyProposal();
        assertThat(p.getName()).isEqualTo("Avistamiento de ballenas");
        assertThat(p.getCategory()).isEqualTo(TypeCategory.ACTIVITY);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PENDING);
        assertThat(p.getResolvedBy()).isNull();

        mockMvc.perform(get("/provider/proposals").with(as(Role.PROVIDER, approved.getId())))
                .andExpect(content().string(containsString("Avistamiento de ballenas")))
                .andExpect(content().string(containsString("Pendiente")));
    }

    @Test
    void propose_existingName_offersTheExistingType() throws Exception {
        // 2a. Ya existe un tipo con ese nombre → se informa y se ofrece el existente
        mockMvc.perform(propose("ACTIVITY", "trekking"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe “Trekking”")))
                .andExpect(content().string(containsString("ya lo podés elegir")));
        assertThat(proposalRepository.count()).isZero();
    }

    @Test
    void propose_existingButInactive_explainsIt() throws Exception {
        mockMvc.perform(propose("EQUIPMENT", "Casco"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("está desactivado")));
        assertThat(proposalRepository.count()).isZero();
    }

    @Test
    void propose_sameNameTwiceWhilePending_isRejected() throws Exception {
        mockMvc.perform(propose("ACTIVITY", "Surf")).andExpect(status().is3xxRedirection());

        mockMvc.perform(propose("ACTIVITY", "SURF"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name"));
        assertThat(proposalRepository.count()).isEqualTo(1);
    }

    @Test
    void propose_withoutNameOrDescription_marksTheFields() throws Exception {
        mockMvc.perform(post("/provider/proposals").with(as(Role.PROVIDER, approved.getId())).with(csrf())
                        .param("category", "ACTIVITY").param("name", " ").param("description", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name", "description"));
    }

    @Test
    void pendingProvider_cannotPropose() throws Exception {
        Provider pending = provider("nuevo@mail.com", "11111111", ProviderStatus.PENDING);

        mockMvc.perform(get("/provider/proposals").with(as(Role.PROVIDER, pending.getId())))
                .andExpect(content().string(containsString("Todavía no podés proponer tipos")));
        mockMvc.perform(post("/provider/proposals").with(as(Role.PROVIDER, pending.getId())).with(csrf())
                        .param("category", "ACTIVITY").param("name", "Surf").param("description", "x"))
                .andExpect(status().isOk());
        assertThat(proposalRepository.count()).isZero();
    }

    @Test
    void activityForm_linksToProposeAType() throws Exception {
        // El enlace del paso 1 está en el formulario de publicar actividad (CU-08 3a)
        mockMvc.perform(get("/provider/proposals").param("category", "equipment")
                        .with(as(Role.PROVIDER, approved.getId())))
                .andExpect(model().attribute("form",
                        org.hamcrest.Matchers.hasProperty("category", org.hamcrest.Matchers.is(TypeCategory.EQUIPMENT))));
    }

    // ---------- CU-13 2a: el administrador resuelve ----------

    @Test
    void admin_seesPendingProposalsOfThatList() throws Exception {
        mockMvc.perform(propose("ACTIVITY", "Avistamiento de ballenas"));

        mockMvc.perform(get("/admin/types/activity").with(as(Role.ADMIN, admin.getId())))
                .andExpect(content().string(containsString("1 propuesta pendiente")))
                .andExpect(content().string(containsString("Avistamiento de ballenas")))
                .andExpect(content().string(containsString("Propuesto por Juan Rodríguez")));
        mockMvc.perform(get("/admin/types/equipment").with(as(Role.ADMIN, admin.getId())))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Avistamiento de ballenas"))));
    }

    @Test
    void accept_createsTheTypeAndTheProviderSeesIt() throws Exception {
        mockMvc.perform(propose("ACTIVITY", "Avistamiento de ballenas"));
        TypeProposal p = onlyProposal();

        mockMvc.perform(post("/admin/types/proposals/{id}/accept", p.getId()).param("category", "activity")
                        .with(as(Role.ADMIN, admin.getId())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/types/activity"))
                .andExpect(flash().attribute("notice", containsString("se agregó")));

        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(p.getResolvedBy().getId()).isEqualTo(admin.getId());
        assertThat(activityTypeRepository.findByActiveTrueOrderByNameAsc())
                .extracting(ActivityType::getName).contains("Avistamiento de ballenas");

        mockMvc.perform(get("/provider/proposals").with(as(Role.PROVIDER, approved.getId())))
                .andExpect(content().string(containsString("Aceptada")));
    }

    @Test
    void reject_doesNotCreateTheTypeAndTheProviderIsNotified() throws Exception {
        mockMvc.perform(propose("EQUIPMENT", "Binoculares"));
        TypeProposal p = onlyProposal();

        mockMvc.perform(post("/admin/types/proposals/{id}/reject", p.getId()).param("category", "equipment")
                        .with(as(Role.ADMIN, admin.getId())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/types/equipment"));

        assertThat(p.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(equipmentTypeRepository.findAll()).extracting(EquipmentType::getName).doesNotContain("Binoculares");

        // 3a: el prestador es notificado
        mockMvc.perform(get("/provider/proposals").with(as(Role.PROVIDER, approved.getId())))
                .andExpect(content().string(containsString("Rechazada")))
                .andExpect(content().string(containsString("el tipo no se creó")));
    }

    @Test
    void resolvingTwice_doesNothingTheSecondTime() throws Exception {
        mockMvc.perform(propose("ACTIVITY", "Surf"));
        TypeProposal p = onlyProposal();
        mockMvc.perform(post("/admin/types/proposals/{id}/reject", p.getId())
                .with(as(Role.ADMIN, admin.getId())).with(csrf()));

        mockMvc.perform(post("/admin/types/proposals/{id}/accept", p.getId())
                        .with(as(Role.ADMIN, admin.getId())).with(csrf()))
                .andExpect(flash().attribute("error", containsString("ya fue resuelta")));

        assertThat(p.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(activityTypeRepository.findAll()).extracting(ActivityType::getName).doesNotContain("Surf");
    }

    @Test
    void onlyAdmins_canResolveProposals() throws Exception {
        mockMvc.perform(propose("ACTIVITY", "Surf"));
        TypeProposal p = onlyProposal();

        mockMvc.perform(post("/admin/types/proposals/{id}/accept", p.getId())
                        .with(as(Role.PROVIDER, approved.getId())).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PENDING);
    }
}
