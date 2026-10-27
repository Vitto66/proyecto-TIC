package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.dto.ActivityOfDay;
import uy.edu.um.proytic2026.dto.PublicBusiness;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.InvalidApiParameterException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests unitarios de la API para otras plataformas (RF-10). */
@ExtendWith(MockitoExtension.class)
class PublicApiServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ActivityInstanceRepository instanceRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LocalityRepository localityRepository;

    private PublicApiService service;

    private final Department rocha = Department.builder().id(1).name("Rocha").build();
    private final Locality laPaloma = Locality.builder().id(10).department(rocha).name("La Paloma").build();
    private final Business laguna = Business.builder().id(3L).locality(laPaloma).name("Kayak Laguna de Rocha")
            .description("Salidas en kayak").address("Ruta 10 km 264")
            .phone("099123456").email("info@laguna.uy").build();

    @BeforeEach
    void setUp() {
        service = new PublicApiService(businessRepository, instanceRepository, reservationRepository,
                departmentRepository, localityRepository);
    }

    // ---------- CU-19 Listar emprendimientos ----------

    @Test
    void listBusinesses_returnsPublicDataOfApprovedOnes() {
        when(businessRepository.findAllByProviderStatus(ProviderStatus.APPROVED)).thenReturn(List.of(laguna));

        List<PublicBusiness> result = service.listBusinesses();

        assertThat(result).containsExactly(new PublicBusiness(3L, "Kayak Laguna de Rocha", "Salidas en kayak",
                "Ruta 10 km 264", "099123456", "info@laguna.uy", "La Paloma", "Rocha"));
    }

    @Test
    void listBusinesses_noneRegistered_returnsEmptyList() {
        when(businessRepository.findAllByProviderStatus(ProviderStatus.APPROVED)).thenReturn(List.of());

        assertThat(service.listBusinesses()).isEmpty();
    }

    // ---------- CU-20 Actividades de un día por departamento o localidad ----------

    private Activity activity(long id, String name) {
        Activity a = Activity.builder().id(id).business(laguna).locality(laPaloma).name(name)
                .durationMinutes(90).price(new BigDecimal("1500")).minAge(12).bookingCutoffMinutes(0).build();
        a.getTypes().add(ActivityType.builder().id(1).name("Actividades acuáticas").build());
        return a;
    }

    private ActivityInstance instance(long id, Activity activity, int hour, Integer maxCapacity) {
        return ActivityInstance.builder().id(id).activity(activity)
                .startAt(DAY.atTime(hour, 0)).maxCapacity(maxCapacity).build();
    }

    private ReservationRepository.OccupiedSeats occupied(long instanceId, long seats) {
        return new ReservationRepository.OccupiedSeats() {
            public Long getInstanceId() {
                return instanceId;
            }

            public Long getSeats() {
                return seats;
            }
        };
    }

    @Test
    void activitiesOfDay_byDepartment_groupsTimesByActivityWithFreeSeats() {
        Activity kayak = activity(20L, "Atardecer en kayak");
        Activity paddle = activity(21L, "Paddle en la laguna");
        when(departmentRepository.existsById(1)).thenReturn(true);
        when(instanceRepository.findPublishedInDepartment(1, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(instance(100L, kayak, 9, 10), instance(101L, paddle, 11, null),
                        instance(102L, kayak, 18, 10)));
        when(reservationRepository.occupiedSeats(any(), any())).thenReturn(List.of(occupied(100L, 4)));

        List<ActivityOfDay> result = service.activitiesOfDay(DAY, 1, null);

        assertThat(result).extracting(ActivityOfDay::name)
                .containsExactly("Atardecer en kayak", "Paddle en la laguna");
        ActivityOfDay first = result.get(0);
        assertThat(first.business()).isEqualTo("Kayak Laguna de Rocha");
        assertThat(first.department()).isEqualTo("Rocha");
        assertThat(first.types()).containsExactly("Actividades acuáticas");
        assertThat(first.times()).containsExactly(
                new ActivityOfDay.Time(100L, DAY.atTime(9, 0), DAY.atTime(10, 30), 6),
                new ActivityOfDay.Time(102L, DAY.atTime(18, 0), DAY.atTime(19, 30), 10));
        // Sin límite de cupos → availableSeats null
        assertThat(result.get(1).times().get(0).availableSeats()).isNull();
    }

    @Test
    void activitiesOfDay_byLocality_usesLocalityQuery() {
        Activity kayak = activity(20L, "Atardecer en kayak");
        when(localityRepository.existsById(10)).thenReturn(true);
        when(instanceRepository.findPublishedInLocality(10, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(instance(100L, kayak, 9, 3)));
        when(reservationRepository.occupiedSeats(any(), any())).thenReturn(List.of(occupied(100L, 5)));

        List<ActivityOfDay> result = service.activitiesOfDay(DAY, null, 10);

        // Más ocupados que el cupo (no debería pasar): nunca negativo
        assertThat(result.get(0).times().get(0).availableSeats()).isZero();
    }

    @Test
    void activitiesOfDay_noActivities_returnsEmptyList() {
        when(departmentRepository.existsById(1)).thenReturn(true);
        when(instanceRepository.findPublishedInDepartment(any(), any(), any())).thenReturn(List.of());

        assertThat(service.activitiesOfDay(DAY, 1, null)).isEmpty();
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void activitiesOfDay_withoutLocation_throwsIndicatingParameter() {
        assertThatThrownBy(() -> service.activitiesOfDay(DAY, null, null))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "departmentId");
    }

    @Test
    void activitiesOfDay_departmentAndLocality_throws() {
        assertThatThrownBy(() -> service.activitiesOfDay(DAY, 1, 10))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "localityId");
    }

    @Test
    void activitiesOfDay_unknownDepartment_throws() {
        when(departmentRepository.existsById(99)).thenReturn(false);

        assertThatThrownBy(() -> service.activitiesOfDay(DAY, 99, null))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "departmentId");
    }

    @Test
    void activitiesOfDay_unknownLocality_throws() {
        when(localityRepository.existsById(99)).thenReturn(false);

        assertThatThrownBy(() -> service.activitiesOfDay(DAY, null, 99))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "localityId");
    }
}
