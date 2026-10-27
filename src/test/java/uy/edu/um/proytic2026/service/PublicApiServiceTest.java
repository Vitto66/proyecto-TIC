package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.dto.ActivityOfDay;
import uy.edu.um.proytic2026.dto.BusinessVerification;
import uy.edu.um.proytic2026.dto.PublicBusiness;
import uy.edu.um.proytic2026.dto.TopActivity;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.InvalidApiParameterException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ProviderRepository providerRepository;

    private PublicApiService service;

    private final Department rocha = Department.builder().id(1).name("Rocha").build();
    private final Locality laPaloma = Locality.builder().id(10).department(rocha).name("La Paloma").build();
    private final Business laguna = Business.builder().id(3L).locality(laPaloma).name("Kayak Laguna de Rocha")
            .description("Salidas en kayak").address("Ruta 10 km 264")
            .phone("099123456").email("info@laguna.uy").build();

    @BeforeEach
    void setUp() {
        service = new PublicApiService(businessRepository, instanceRepository, reservationRepository,
                departmentRepository, localityRepository, reviewRepository, activityRepository, providerRepository);
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

    // ---------- CU-21 Top 3 de actividades del mes por departamento ----------

    private ReviewRepository.RatingStats stats(long activityId, double average, long count) {
        return new ReviewRepository.RatingStats() {
            public Long getActivityId() {
                return activityId;
            }

            public Double getAverage() {
                return average;
            }

            public Long getCount() {
                return count;
            }
        };
    }

    @Test
    void topActivitiesOfMonth_returnsTopThreeInRepositoryOrder() {
        when(departmentRepository.existsById(1)).thenReturn(true);
        when(reviewRepository.ratingStatsInDepartment(1, LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0)))
                .thenReturn(List.of(stats(21L, 5.0, 2), stats(20L, 4.666666, 3), stats(22L, 4.5, 2),
                        stats(23L, 3.0, 1)));
        when(activityRepository.findAllById(List.of(21L, 20L, 22L)))
                .thenReturn(List.of(activity(20L, "Atardecer en kayak"), activity(21L, "Paddle"),
                        activity(22L, "Cabalgata")));

        List<TopActivity> result = service.topActivitiesOfMonth(1, "2026-10");

        assertThat(result).containsExactly(
                new TopActivity(1, 21L, "Paddle", "Kayak Laguna de Rocha", "La Paloma", "Rocha", 5.0, 2),
                new TopActivity(2, 20L, "Atardecer en kayak", "Kayak Laguna de Rocha", "La Paloma", "Rocha", 4.67, 3),
                new TopActivity(3, 22L, "Cabalgata", "Kayak Laguna de Rocha", "La Paloma", "Rocha", 4.5, 2));
    }

    @Test
    void topActivitiesOfMonth_noReviewsInMonth_returnsEmptyList() {
        when(departmentRepository.existsById(1)).thenReturn(true);
        when(reviewRepository.ratingStatsInDepartment(any(), any(), any())).thenReturn(List.of());

        assertThat(service.topActivitiesOfMonth(1, "2026-10")).isEmpty();
        verifyNoInteractions(activityRepository);
    }

    @Test
    void topActivitiesOfMonth_december_endsOnFirstOfJanuary() {
        when(departmentRepository.existsById(1)).thenReturn(true);
        when(reviewRepository.ratingStatsInDepartment(any(), any(), any())).thenReturn(List.of());

        service.topActivitiesOfMonth(1, "2026-12");

        verify(reviewRepository).ratingStatsInDepartment(1, LocalDateTime.of(2026, 12, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0));
    }

    @Test
    void topActivitiesOfMonth_invalidMonth_throwsIndicatingParameter() {
        assertThatThrownBy(() -> service.topActivitiesOfMonth(1, "10-2026"))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "month");
        assertThatThrownBy(() -> service.topActivitiesOfMonth(1, "2026-13"))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "month");
    }

    @Test
    void topActivitiesOfMonth_unknownDepartment_throwsIndicatingParameter() {
        when(departmentRepository.existsById(99)).thenReturn(false);

        assertThatThrownBy(() -> service.topActivitiesOfMonth(99, "2026-10"))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "departmentId");
    }

    // ---------- CU-22 Verificar un emprendimiento ----------

    private Provider provider(ProviderStatus status) {
        return Provider.builder().id(7L).personType(PersonType.LEGAL).status(status)
                .rut("211234560018").legalName("Laguna Aventuras S.A.").build();
    }

    @Test
    void verifyBusiness_approvedWithBusiness_returnsRegisteredAndApproved() {
        when(providerRepository.findByRut("211234560018")).thenReturn(Optional.of(provider(ProviderStatus.APPROVED)));
        when(businessRepository.findByProviderId(7L)).thenReturn(Optional.of(laguna));

        BusinessVerification result = service.verifyBusiness("211234560018", null);

        assertThat(result).isEqualTo(new BusinessVerification(true, true, ProviderStatus.APPROVED,
                "Kayak Laguna de Rocha"));
    }

    @Test
    void verifyBusiness_byDocumentWithDotsAndDash_comparesOnlyDigits() {
        when(providerRepository.findByDocument("45678901")).thenReturn(Optional.of(provider(ProviderStatus.APPROVED)));
        when(businessRepository.findByProviderId(7L)).thenReturn(Optional.empty());

        BusinessVerification result = service.verifyBusiness(null, " 4.567.890-1 ");

        // Aprobado, pero todavía no registró su emprendimiento
        assertThat(result).isEqualTo(new BusinessVerification(true, true, ProviderStatus.APPROVED, null));
    }

    @Test
    void verifyBusiness_pendingOrRejected_returnsItsStatus() {
        when(providerRepository.findByRut("211234560018")).thenReturn(Optional.of(provider(ProviderStatus.PENDING)));
        when(businessRepository.findByProviderId(7L)).thenReturn(Optional.empty());

        BusinessVerification result = service.verifyBusiness("211234560018", null);

        assertThat(result.registered()).isTrue();
        assertThat(result.approved()).isFalse();
        assertThat(result.status()).isEqualTo(ProviderStatus.PENDING);
    }

    @Test
    void verifyBusiness_notRegistered_returnsNotRegistered() {
        when(providerRepository.findByRut("219999990018")).thenReturn(Optional.empty());

        assertThat(service.verifyBusiness("219999990018", null)).isEqualTo(BusinessVerification.notRegistered());
    }

    @Test
    void verifyBusiness_neitherOrBoth_throwsIndicatingParameter() {
        assertThatThrownBy(() -> service.verifyBusiness(null, "  "))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "rut");
        assertThatThrownBy(() -> service.verifyBusiness("211234560018", "45678901"))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "document");
    }

    @Test
    void verifyBusiness_withoutDigits_throwsIndicatingParameter() {
        assertThatThrownBy(() -> service.verifyBusiness(null, "abc"))
                .isInstanceOf(InvalidApiParameterException.class)
                .hasFieldOrPropertyWithValue("parameter", "document");
    }
}
