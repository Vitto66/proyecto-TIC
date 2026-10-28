package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Foto de portada de las actividades: el prestador la sube al publicar (CU-08) o al modificar
 * (CU-10), y se ve en Explorar, en el inicio y en el detalle. Contra H2 y con Spring Security activo.
 */
@SpringBootTest
@Transactional
class ActivityPhotoIntegrationTest {

    private static final String URL = "/provider/activities";

    /** Los primeros bytes de un PNG, seguidos de relleno: alcanza para que se reconozca como PNG. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LocalityRepository localityRepository;
    @Autowired private ActivityTypeRepository activityTypeRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityPhotoRepository photoRepository;

    private MockMvc mockMvc;
    private Department rocha;
    private Locality laPaloma;
    private ActivityType kayak;
    private Provider provider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        kayak = activityTypeRepository.save(ActivityType.builder().name("Actividades acuáticas").build());

        User user = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        provider = providerRepository.save(Provider.builder().user(user).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901")
                .address("Ruta 10").build());
        user.setProvider(provider);
        businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("Salidas").address("Ruta 10").phone("099").email("a@b.uy").build());
    }

    private RequestPostProcessor session() {
        AuthenticatedUser principal = new AuthenticatedUser(provider.getId(), "juan@mail.com", Role.PROVIDER, "Juan");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_PROVIDER"))));
    }

    private MultiValueMap<String, String> validParams(String name) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("description", "Recorrido por la laguna al atardecer.");
        params.add("departmentId", rocha.getId().toString());
        params.add("localityId", laPaloma.getId().toString());
        params.add("durationMinutes", "150");
        params.add("price", "1500");
        params.add("minAge", "0");
        params.add("bookingCutoffMinutes", "0");
        params.add("activityTypeIds", kayak.getId().toString());
        return params;
    }

    private static MockMultipartFile photo(String name, String type, byte[] bytes) {
        return new MockMultipartFile("photo", name, type, bytes);
    }

    private Activity onlyActivity() {
        return activityRepository.findAll().stream().filter(a -> a.getName().equals("Atardecer en kayak"))
                .findFirst().orElseThrow();
    }

    @Test
    void publish_withPhoto_savesIt_andShowsItEverywhere() throws Exception {
        mockMvc.perform(multipart(URL).file(photo("laguna.png", "image/png", PNG))
                        .params(validParams("Atardecer en kayak")).with(session()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(URL));

        Activity activity = onlyActivity();
        ActivityPhoto saved = photoRepository.findById(activity.getId()).orElseThrow();
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getImage()).isEqualTo(PNG);

        String photoPath = "/activities/" + activity.getId() + "/photo";
        // La foto es pública
        mockMvc.perform(get(photoPath))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        // Se ve en Explorar, en el inicio, en el detalle y en "Mis actividades"
        mockMvc.perform(get("/activities")).andExpect(content().string(containsString(photoPath + "?v=")));
        mockMvc.perform(get("/")).andExpect(content().string(containsString(photoPath + "?v=")));
        mockMvc.perform(get("/activities/" + activity.getId()))
                .andExpect(content().string(containsString("class=\"detail-photo\"")))
                .andExpect(content().string(containsString(photoPath + "?v=")));
        mockMvc.perform(get(URL).with(session())).andExpect(content().string(containsString(photoPath + "?v=")));
    }

    @Test
    void publish_withoutPhoto_works_andShowsTheIllustration() throws Exception {
        mockMvc.perform(multipart(URL).params(validParams("Atardecer en kayak")).with(session()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Activity activity = onlyActivity();
        assertThat(photoRepository.findById(activity.getId())).isEmpty();
        mockMvc.perform(get("/activities/" + activity.getId() + "/photo")).andExpect(status().isNotFound());
        mockMvc.perform(get("/activities"))
                .andExpect(content().string(not(containsString("/photo?v="))))
                .andExpect(content().string(containsString("data-scene=\"rio\"")));
        mockMvc.perform(get(URL).with(session())).andExpect(content().string(containsString("Agregar foto")));
    }

    @Test
    void publish_withAFileThatIsNotAnImage_isRejected_andNothingIsPublished() throws Exception {
        mockMvc.perform(multipart(URL).file(photo("foto.jpg", "image/jpeg", "<svg onload=alert(1)>".getBytes()))
                        .params(validParams("Atardecer en kayak")).with(session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("activity-form"))
                .andExpect(content().string(containsString("JPG, PNG o WebP")));

        assertThat(activityRepository.findAll()).noneMatch(a -> a.getName().equals("Atardecer en kayak"));
        assertThat(photoRepository.findAll()).isEmpty();
    }

    @Test
    void publish_withOtherErrors_asksToChooseThePhotoAgain() throws Exception {
        MultiValueMap<String, String> params = validParams("Atardecer en kayak");
        params.set("name", "");
        mockMvc.perform(multipart(URL).file(photo("laguna.png", "image/png", PNG))
                        .params(params).with(session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Volvé a elegir la foto")));
        assertThat(photoRepository.findAll()).isEmpty();
    }

    @Test
    void edit_replacesThePhoto_andCanRemoveIt() throws Exception {
        mockMvc.perform(multipart(URL).file(photo("laguna.png", "image/png", PNG))
                        .params(validParams("Atardecer en kayak")).with(session()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Long id = onlyActivity().getId();

        // El formulario muestra la foto actual y la opción de sacarla
        mockMvc.perform(get(URL + "/" + id + "/edit").with(session()))
                .andExpect(content().string(containsString("/activities/" + id + "/photo?v=")))
                .andExpect(content().string(containsString("Sacar la foto")))
                .andExpect(content().string(containsString("multipart/form-data")));

        // Una foto nueva reemplaza a la anterior
        mockMvc.perform(multipart(URL + "/" + id).file(photo("nueva.jpg", "image/jpeg", JPG))
                        .params(validParams("Atardecer en kayak")).with(session()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(photoRepository.findById(id).orElseThrow().getContentType()).isEqualTo("image/jpeg");

        // Guardar sin elegir otra deja la que estaba
        mockMvc.perform(multipart(URL + "/" + id).params(validParams("Atardecer en kayak"))
                        .with(session()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(photoRepository.findById(id)).isPresent();

        // Sacar la foto
        MultiValueMap<String, String> params = validParams("Atardecer en kayak");
        params.add("removePhoto", "true");
        mockMvc.perform(multipart(URL + "/" + id).params(params).with(session()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(photoRepository.findById(id)).isEmpty();
    }

    @Test
    void newForm_offersThePhotoField() throws Exception {
        mockMvc.perform(get(URL + "/new").with(session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"photo\"")))
                .andExpect(content().string(containsString("Foto de portada")));
    }

    @Test
    void externalPhoto_isShownWithItsAddress_andThePhotoUrlRedirectsToIt() throws Exception {
        mockMvc.perform(multipart(URL).params(validParams("Atardecer en kayak")).with(session()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Long id = onlyActivity().getId();
        String external = "https://commons.wikimedia.org/wiki/Special:FilePath/Laguna_Garzon_01.jpg?width=1600";
        photoRepository.save(ActivityPhoto.builder().activityId(id).externalUrl(external)
                .updatedAt(LocalDateTime.now()).build());

        mockMvc.perform(get("/activities/" + id + "/photo"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(external));
        mockMvc.perform(get("/activities")).andExpect(content().string(containsString(external)));
        // Si la foto externa no carga, queda la ilustración de abajo
        mockMvc.perform(get("/")).andExpect(content().string(containsString("onerror=\"this.remove()\"")));
    }
}
