package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.dto.ActivitySummary;
import uy.edu.um.proytic2026.dto.EquipmentSource;
import uy.edu.um.proytic2026.dto.PublishActivityRequest;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityEquipment;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.EquipmentType;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.BusinessRequiredException;
import uy.edu.um.proytic2026.exception.InvalidLocationException;
import uy.edu.um.proytic2026.exception.InvalidSelectionException;
import uy.edu.um.proytic2026.exception.ProviderNotApprovedException;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.EquipmentTypeRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.service.ActivityService.Access;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** Tests unitarios del CU-08 Publicar una actividad. */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final Long PROVIDER_ID = 7L;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityTypeRepository activityTypeRepository;

    @Mock
    private EquipmentTypeRepository equipmentTypeRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private LocationService locationService;

    private ActivityService service;

    private final Department rocha = Department.builder().id(1).name("Rocha").build();
    private final Locality laPaloma = Locality.builder().id(10).department(rocha).name("La Paloma").build();
    private final Business business = Business.builder().id(50L).name("Kayak Laguna").locality(laPaloma).build();

    private final ActivityType kayak = ActivityType.builder().id(1).name("Actividades acuáticas").active(true).build();
    private final ActivityType naturaleza = ActivityType.builder().id(2).name("Turismo de naturaleza").active(true).build();
    private final ActivityType inactivo = ActivityType.builder().id(3).name("Viejo").active(false).build();

    private final EquipmentType chaleco = EquipmentType.builder().id(20).name("Chaleco salvavidas").active(true).build();
    private final EquipmentType gorro = EquipmentType.builder().id(21).name("Gorro").active(true).build();

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityRepository, activityTypeRepository, equipmentTypeRepository,
                businessRepository, providerRepository, locationService);
    }

    // ---------- Armado de datos ----------

    private void providerWithStatus(ProviderStatus status) {
        Provider provider = Provider.builder().id(PROVIDER_ID).personType(PersonType.PHYSICAL).status(status).build();
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
    }

    private void approvedWithBusiness() {
        providerWithStatus(ProviderStatus.APPROVED);
        when(businessRepository.findByProviderId(PROVIDER_ID)).thenReturn(Optional.of(business));
    }

    private PublishActivityRequest request(List<Integer> typeIds, Map<Integer, EquipmentSource> equipment) {
        return PublishActivityRequest.builder()
                .name("  Atardecer en kayak ").description(" Recorrido por la laguna. ")
                .departmentId(1).localityId(10)
                .durationMinutes(150).price(new BigDecimal("1500.50")).minAge(12).bookingCutoffMinutes(60)
                .activityTypeIds(typeIds).equipment(equipment)
                .build();
    }

    private Activity publishAndCapture(PublishActivityRequest request) {
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
            Activity a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });
        service.publish(PROVIDER_ID, request);
        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(saved.capture());
        return saved.getValue();
    }

    // ---------- access ----------

    @Test
    void access_dependsOnApprovalAndBusiness() {
        providerWithStatus(ProviderStatus.PENDING);
        assertThat(service.access(PROVIDER_ID)).isEqualTo(Access.NOT_APPROVED);

        providerWithStatus(ProviderStatus.APPROVED);
        when(businessRepository.existsByProviderId(PROVIDER_ID)).thenReturn(false);
        assertThat(service.access(PROVIDER_ID)).isEqualTo(Access.NO_BUSINESS);

        when(businessRepository.existsByProviderId(PROVIDER_ID)).thenReturn(true);
        assertThat(service.access(PROVIDER_ID)).isEqualTo(Access.ALLOWED);
    }

    // ---------- publish ----------

    @Test
    void publish_validData_savesActiveActivityOfTheBusiness() {
        approvedWithBusiness();
        when(locationService.locality(1, 10)).thenReturn(laPaloma);
        when(activityTypeRepository.findAllById(anyCollection())).thenReturn(List.of(kayak, naturaleza));

        Activity a = publishAndCapture(request(List.of(1, 2), Map.of()));

        assertThat(a.getBusiness()).isSameAs(business);
        assertThat(a.getLocality()).isSameAs(laPaloma);
        assertThat(a.getName()).isEqualTo("Atardecer en kayak");
        assertThat(a.getDescription()).isEqualTo("Recorrido por la laguna.");
        assertThat(a.getDurationMinutes()).isEqualTo(150);
        assertThat(a.getPrice()).isEqualByComparingTo("1500.50");
        assertThat(a.getMinAge()).isEqualTo(12);
        assertThat(a.getBookingCutoffMinutes()).isEqualTo(60);
        // Paso 7: queda publicada
        assertThat(a.isActive()).isTrue();
        // Paso 3: uno o más tipos
        assertThat(a.getTypes()).containsExactlyInAnyOrder(kayak, naturaleza);
        assertThat(a.getEquipment()).isEmpty();
    }

    @Test
    void publish_withEquipment_storesWhoProvidesEach() {
        approvedWithBusiness();
        when(locationService.locality(1, 10)).thenReturn(laPaloma);
        when(activityTypeRepository.findAllById(anyCollection())).thenReturn(List.of(kayak));
        when(equipmentTypeRepository.findAllById(anyCollection())).thenReturn(List.of(chaleco, gorro));

        Map<Integer, EquipmentSource> equipment = new HashMap<>();
        equipment.put(20, EquipmentSource.PROVIDER);
        equipment.put(21, EquipmentSource.TOURIST);
        equipment.put(22, null); // "No se usa": se ignora

        Activity a = publishAndCapture(request(List.of(1), equipment));

        assertThat(a.getEquipment()).hasSize(2);
        assertThat(a.getEquipment()).allSatisfy(e -> assertThat(e.getActivity()).isSameAs(a));
        ActivityEquipment provided = a.getEquipment().stream()
                .filter(e -> e.getEquipmentType() == chaleco).findFirst().orElseThrow();
        ActivityEquipment brought = a.getEquipment().stream()
                .filter(e -> e.getEquipmentType() == gorro).findFirst().orElseThrow();
        assertThat(provided.isProvidedByProvider()).isTrue();
        assertThat(brought.isProvidedByProvider()).isFalse();
    }

    @Test
    void publish_returnsSummaryForTheList() {
        approvedWithBusiness();
        when(locationService.locality(1, 10)).thenReturn(laPaloma);
        when(activityTypeRepository.findAllById(anyCollection())).thenReturn(List.of(kayak));
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivitySummary summary = service.publish(PROVIDER_ID, request(List.of(1), Map.of()));

        assertThat(summary.location()).isEqualTo("La Paloma, Rocha");
        assertThat(summary.durationLabel()).isEqualTo("2 h 30 min");
        assertThat(summary.priceLabel()).isEqualTo("$ 1.500,50");
        assertThat(summary.typesLabel()).isEqualTo("Actividades acuáticas");
    }

    @Test
    void publish_pendingProvider_isRejected() {
        providerWithStatus(ProviderStatus.PENDING);

        assertThatThrownBy(() -> service.publish(PROVIDER_ID, request(List.of(1), Map.of())))
                .isInstanceOf(ProviderNotApprovedException.class);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void publish_withoutBusiness_isRejected() {
        providerWithStatus(ProviderStatus.APPROVED);
        when(businessRepository.findByProviderId(PROVIDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(PROVIDER_ID, request(List.of(1), Map.of())))
                .isInstanceOf(BusinessRequiredException.class);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void publish_invalidLocation_isRejected() {
        approvedWithBusiness();
        when(locationService.locality(1, 10)).thenThrow(new InvalidLocationException());

        assertThatThrownBy(() -> service.publish(PROVIDER_ID, request(List.of(1), Map.of())))
                .isInstanceOf(InvalidLocationException.class);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void publish_unknownActivityType_isRejectedOnTypesField() {
        approvedWithBusiness();
        when(locationService.locality(1, 10)).thenReturn(laPaloma);
        when(activityTypeRepository.findAllById(anyCollection())).thenReturn(List.of(kayak)); // falta el 99

        assertThatThrownBy(() -> service.publish(PROVIDER_ID, request(List.of(1, 99), Map.of())))
                .isInstanceOf(InvalidSelectionException.class)
                .extracting("field").isEqualTo("activityTypeIds");
    }

    @Test
    void publish_inactiveActivityType_isRejected() {
        approvedWithBusiness();
        when(locationService.locality(1, 10)).thenReturn(laPaloma);
        when(activityTypeRepository.findAllById(anyCollection())).thenReturn(List.of(inactivo));

        assertThatThrownBy(() -> service.publish(PROVIDER_ID, request(List.of(3), Map.of())))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    void publish_noActivityTypes_isRejected() {
        approvedWithBusiness();
        when(locationService.locality(1, 10)).thenReturn(laPaloma);

        assertThatThrownBy(() -> service.publish(PROVIDER_ID, request(List.of(), Map.of())))
                .isInstanceOf(InvalidSelectionException.class)
                .extracting("field").isEqualTo("activityTypeIds");
    }

    @Test
    void publish_unknownEquipment_isRejectedOnEquipmentField() {
        approvedWithBusiness();
        when(locationService.locality(1, 10)).thenReturn(laPaloma);
        when(activityTypeRepository.findAllById(anyCollection())).thenReturn(List.of(kayak));
        when(equipmentTypeRepository.findAllById(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.publish(PROVIDER_ID, request(List.of(1), Map.of(77, EquipmentSource.PROVIDER))))
                .isInstanceOf(InvalidSelectionException.class)
                .extracting("field").isEqualTo("equipment");
        verify(activityRepository, never()).save(any());
    }

    // ---------- formDefaults ----------

    @Test
    void formDefaults_startsWithTheBusinessLocation() {
        approvedWithBusiness();

        PublishActivityRequest form = service.formDefaults(PROVIDER_ID);

        assertThat(form.getDepartmentId()).isEqualTo(1);
        assertThat(form.getLocalityId()).isEqualTo(10);
        assertThat(form.getMinAge()).isZero();
        assertThat(form.getBookingCutoffMinutes()).isZero();
    }

    // ---------- Formato para mostrar ----------

    @Test
    void summaryLabels_formatDurationAndPrice() {
        assertThat(ActivitySummary.formatMinutes(45)).isEqualTo("45 min");
        assertThat(ActivitySummary.formatMinutes(120)).isEqualTo("2 h");
        assertThat(ActivitySummary.formatMinutes(90)).isEqualTo("1 h 30 min");
    }
}
