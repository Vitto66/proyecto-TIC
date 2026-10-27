package uy.edu.um.proytic2026.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.entity.Review;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityTypeRepository activityTypeRepository;

    @Autowired
    private ActivityInstanceRepository instanceRepository;

    @Autowired
    private TouristRepository touristRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private MockMvc mockMvc;

    private Department rocha;
    private Department maldonado;
    private Locality laPaloma;
    private Locality puntaDelEste;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // En H2 las tablas de ubicación arrancan vacías; en Supabase ya tienen datos
        rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        maldonado = departmentRepository.save(Department.builder().name("Maldonado").build());
        laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        puntaDelEste = localityRepository.save(Locality.builder().department(maldonado).name("Punta del Este").build());
    }

    private Business business(String name, String email, String document, ProviderStatus status) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder()
                .user(user).personType(PersonType.PHYSICAL).status(status)
                .firstName("Juan").lastName("Rodríguez").document(document).address("Ruta 10 km 264")
                .build());
        return businessRepository.save(Business.builder().provider(provider).locality(laPaloma).name(name)
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

    // ---------- CU-20 Actividades de un día por departamento o localidad ----------

    private static final LocalDateTime DAY = LocalDateTime.of(2030, 3, 15, 0, 0);

    private Activity activity(Business business, Locality locality, String name, boolean active) {
        Activity a = Activity.builder().business(business).locality(locality).name(name)
                .description("Una linda salida").durationMinutes(120).price(new BigDecimal("1500"))
                .minAge(0).bookingCutoffMinutes(0).active(active).build();
        a.getTypes().add(activityTypeRepository.save(ActivityType.builder().name("Tipo de " + name).build()));
        return activityRepository.save(a);
    }

    private ActivityInstance date(Activity activity, LocalDateTime start, Integer maxCapacity) {
        return instanceRepository.save(ActivityInstance.builder()
                .activity(activity).startAt(start).maxCapacity(maxCapacity).build());
    }

    private void reservation(ActivityInstance date, int participants, ReservationStatus status) {
        User user = userRepository.save(User.builder().email("turista" + date.getId() + status + "@mail.com")
                .passwordHash("x").build());
        Tourist tourist = touristRepository.save(Tourist.builder().user(user)
                .firstName("Ana").lastName("Pérez").nationalId("3" + date.getId() + status.ordinal()).build());
        reservationRepository.save(Reservation.builder().tourist(tourist).instance(date)
                .participants(participants).totalPrice(new BigDecimal("1500")).status(status)
                .createdAt(DAY.minusDays(1)).build());
    }

    @Test
    void activitiesOfDay_byDepartment_returnsActivitiesWithTimesAndFreeSeats() throws Exception {
        Business laguna = business("Kayak Laguna de Rocha", "kayak@mail.com", "45678901", ProviderStatus.APPROVED);
        Activity kayak = activity(laguna, laPaloma, "Atardecer en kayak", true);
        Activity other = activity(laguna, puntaDelEste, "Paseo por la península", true);
        Activity cancelled = activity(laguna, laPaloma, "Actividad dada de baja", false);

        ActivityInstance morning = date(kayak, DAY.withHour(9), 10);
        date(kayak, DAY.withHour(18), null);
        date(kayak, DAY.plusDays(1).withHour(9), 10);   // otro día
        date(other, DAY.withHour(10), 10);              // otro departamento
        date(cancelled, DAY.withHour(11), 10);          // dada de baja
        reservation(morning, 3, ReservationStatus.CONFIRMED);
        reservation(morning, 2, ReservationStatus.PENDING); // todavía no ocupa cupos

        mockMvc.perform(get("/api/activities")
                        .param("date", "2030-03-15").param("departmentId", rocha.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Atardecer en kayak"))
                .andExpect(jsonPath("$[0].business").value("Kayak Laguna de Rocha"))
                .andExpect(jsonPath("$[0].locality").value("La Paloma"))
                .andExpect(jsonPath("$[0].times", hasSize(2)))
                .andExpect(jsonPath("$[0].times[0].start").value("2030-03-15T09:00:00"))
                .andExpect(jsonPath("$[0].times[0].availableSeats").value(7))
                .andExpect(jsonPath("$[0].times[1].availableSeats").value(nullValue()));
    }

    @Test
    void activitiesOfDay_byLocality_returnsOnlyThatLocality() throws Exception {
        Business laguna = business("Kayak Laguna de Rocha", "kayak@mail.com", "45678901", ProviderStatus.APPROVED);
        date(activity(laguna, laPaloma, "Atardecer en kayak", true), DAY.withHour(9), 10);
        date(activity(laguna, puntaDelEste, "Paseo por la península", true), DAY.withHour(10), 10);

        mockMvc.perform(get("/api/activities")
                        .param("date", "2030-03-15").param("localityId", puntaDelEste.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Paseo por la península"));
    }

    @Test
    void activitiesOfDay_noActivities_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/activities")
                        .param("date", "2030-03-15").param("departmentId", rocha.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void activitiesOfDay_invalidDate_returns400WithParameter() throws Exception {
        mockMvc.perform(get("/api/activities")
                        .param("date", "15/03/2030").param("departmentId", rocha.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("date"));
    }

    @Test
    void activitiesOfDay_missingDate_returns400WithParameter() throws Exception {
        mockMvc.perform(get("/api/activities").param("departmentId", rocha.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("date"));
    }

    @Test
    void activitiesOfDay_unknownDepartment_returns400WithParameter() throws Exception {
        mockMvc.perform(get("/api/activities").param("date", "2030-03-15").param("departmentId", "9999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("departmentId"));
    }

    @Test
    void activitiesOfDay_withoutLocation_returns400() throws Exception {
        mockMvc.perform(get("/api/activities").param("date", "2030-03-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("departmentId"));
    }

    // ---------- CU-21 Top 3 de actividades del mes por departamento ----------

    private int touristCount = 0;

    /** Una reserva finalizada de esa actividad, valorada con ese puntaje en esa fecha. */
    private void review(Activity activity, int rating, LocalDateTime createdAt) {
        touristCount++;
        ActivityInstance date = date(activity, createdAt.minusDays(1).withHour(9).plusMinutes(touristCount), 50);
        User user = userRepository.save(User.builder().email("valora" + touristCount + "@mail.com")
                .passwordHash("x").build());
        Tourist tourist = touristRepository.save(Tourist.builder().user(user)
                .firstName("Ana").lastName("Pérez").nationalId("5000000" + touristCount).build());
        Reservation reservation = reservationRepository.save(Reservation.builder().tourist(tourist).instance(date)
                .participants(1).totalPrice(new BigDecimal("1500")).status(ReservationStatus.FINISHED)
                .createdAt(createdAt.minusDays(3)).build());
        reviewRepository.save(Review.builder().reservation(reservation).rating(rating)
                .comment("Muy lindo").createdAt(createdAt).build());
    }

    @Test
    void topActivitiesOfMonth_returnsBestThreeOfTheMonthInDepartment() throws Exception {
        Business laguna = business("Kayak Laguna de Rocha", "kayak@mail.com", "45678901", ProviderStatus.APPROVED);
        Activity kayak = activity(laguna, laPaloma, "Atardecer en kayak", true);
        Activity paddle = activity(laguna, laPaloma, "Paddle en la laguna", true);
        Activity surf = activity(laguna, laPaloma, "Clase de surf", true);
        Activity trekking = activity(laguna, laPaloma, "Trekking al faro", true);
        Activity cancelled = activity(laguna, laPaloma, "Actividad dada de baja", false);
        Activity otherDepartment = activity(laguna, puntaDelEste, "Paseo por la península", true);
        LocalDateTime october = LocalDateTime.of(2030, 10, 10, 12, 0);

        review(paddle, 5, october);                   // paddle: 4.5 con 2 valoraciones
        review(paddle, 4, october);
        review(kayak, 5, october);                    // kayak: 4.5 con 4 → le gana el empate a paddle (3b)
        review(kayak, 4, october);
        review(kayak, 5, october.plusDays(1));
        review(kayak, 4, october.plusDays(2));
        review(surf, 5, october);                     // surf: 5.0 con 1
        review(trekking, 2, october);                 // trekking: 2.0, queda cuarto
        review(trekking, 1, october.minusMonths(1));  // de septiembre: no cuenta
        review(cancelled, 5, october);                // dada de baja: no participa
        review(otherDepartment, 5, october);          // otro departamento: no participa

        mockMvc.perform(get("/api/activities/top")
                        .param("departmentId", rocha.getId().toString()).param("month", "2030-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Clase de surf"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].averageRating").value(5.0))
                .andExpect(jsonPath("$[1].name").value("Atardecer en kayak"))
                .andExpect(jsonPath("$[1].reviewsInMonth").value(4))
                .andExpect(jsonPath("$[2].name").value("Paddle en la laguna"))
                .andExpect(jsonPath("$[2].averageRating").value(4.5));
    }

    @Test
    void topActivitiesOfMonth_lessThanThreeRated_returnsThoseThatExist() throws Exception {
        Business laguna = business("Kayak Laguna de Rocha", "kayak@mail.com", "45678901", ProviderStatus.APPROVED);
        review(activity(laguna, laPaloma, "Atardecer en kayak", true), 4, LocalDateTime.of(2030, 10, 10, 12, 0));

        mockMvc.perform(get("/api/activities/top")
                        .param("departmentId", rocha.getId().toString()).param("month", "2030-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Atardecer en kayak"));
    }

    @Test
    void topActivitiesOfMonth_invalidMonth_returns400WithParameter() throws Exception {
        mockMvc.perform(get("/api/activities/top")
                        .param("departmentId", rocha.getId().toString()).param("month", "octubre"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("month"));
    }

    @Test
    void topActivitiesOfMonth_unknownDepartment_returns400WithParameter() throws Exception {
        mockMvc.perform(get("/api/activities/top").param("departmentId", "9999").param("month", "2030-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("departmentId"));
    }
}
