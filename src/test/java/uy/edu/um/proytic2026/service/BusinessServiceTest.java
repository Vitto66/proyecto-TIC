package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import uy.edu.um.proytic2026.dto.BusinessSummary;
import uy.edu.um.proytic2026.dto.RegisterBusinessRequest;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.exception.BusinessAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidLocationException;
import uy.edu.um.proytic2026.exception.ProviderNotApprovedException;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests unitarios del CU-07 Registrar el emprendimiento. */
@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    private static final Long PROVIDER_ID = 7L;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LocalityRepository localityRepository;

    private BusinessService service;

    private final Department rocha = Department.builder().id(1).name("Rocha").build();
    private final Department maldonado = Department.builder().id(2).name("Maldonado").build();
    private final Locality laPaloma = Locality.builder().id(10).department(rocha).name("La Paloma").build();

    @BeforeEach
    void setUp() {
        service = new BusinessService(businessRepository, providerRepository, departmentRepository, localityRepository);
    }

    // ---------- Armado de datos ----------

    private Provider provider(PersonType type, ProviderStatus status) {
        User user = User.builder().id(PROVIDER_ID).email("juan@mail.com").phone("099111222").passwordHash("x").build();
        Provider.ProviderBuilder p = Provider.builder().id(PROVIDER_ID).user(user).personType(type).status(status);
        if (type == PersonType.LEGAL) {
            p.rut("211234560018").legalName("Laguna Aventuras S.A.").businessName("Kayak Laguna de Rocha");
        } else {
            p.firstName("Juan").lastName("Rodríguez").document("45678901");
        }
        return p.build();
    }

    private void providerExists(Provider provider) {
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
    }

    private RegisterBusinessRequest request(Integer departmentId, Integer localityId) {
        return RegisterBusinessRequest.builder()
                .name("  Kayak Laguna de Rocha ").departmentId(departmentId).localityId(localityId)
                .address(" Ruta 10 km 264 ").phone(" 099 123 456 ").email(" Info@Laguna.UY ")
                .description(" Salidas en kayak por la laguna. ")
                .build();
    }

    private void saveReturnsWithId() {
        when(businessRepository.saveAndFlush(any(Business.class))).thenAnswer(inv -> {
            Business b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });
    }

    // ---------- register ----------

    @Test
    void register_approvedProvider_savesBusinessLinkedToProvider() {
        Provider provider = provider(PersonType.PHYSICAL, ProviderStatus.APPROVED);
        providerExists(provider);
        when(businessRepository.existsByProviderId(PROVIDER_ID)).thenReturn(false);
        when(localityRepository.findById(10)).thenReturn(Optional.of(laPaloma));
        saveReturnsWithId();

        BusinessSummary summary = service.register(PROVIDER_ID, request(1, 10));

        ArgumentCaptor<Business> saved = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).saveAndFlush(saved.capture());
        Business b = saved.getValue();
        assertThat(b.getProvider()).isSameAs(provider);
        assertThat(b.getLocality()).isSameAs(laPaloma);
        // Se guardan sin espacios de más y el correo en minúsculas
        assertThat(b.getName()).isEqualTo("Kayak Laguna de Rocha");
        assertThat(b.getAddress()).isEqualTo("Ruta 10 km 264");
        assertThat(b.getPhone()).isEqualTo("099 123 456");
        assertThat(b.getEmail()).isEqualTo("info@laguna.uy");
        assertThat(b.getDescription()).isEqualTo("Salidas en kayak por la laguna.");

        assertThat(summary.id()).isEqualTo(100L);
        assertThat(summary.location()).isEqualTo("La Paloma, Rocha");
    }

    @Test
    void register_pendingProvider_isRejected() {
        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.PENDING));

        assertThatThrownBy(() -> service.register(PROVIDER_ID, request(1, 10)))
                .isInstanceOf(ProviderNotApprovedException.class);
        verify(businessRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_rejectedProvider_isRejected() {
        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.REJECTED));

        assertThatThrownBy(() -> service.register(PROVIDER_ID, request(1, 10)))
                .isInstanceOf(ProviderNotApprovedException.class);
    }

    @Test
    void register_unknownProvider_isRejected() {
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(PROVIDER_ID, request(1, 10)))
                .isInstanceOf(ProviderNotApprovedException.class);
    }

    @Test
    void register_alreadyHasBusiness_throwsAlreadyRegistered() {
        // 1a. Ya tiene su emprendimiento registrado
        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.APPROVED));
        when(businessRepository.existsByProviderId(PROVIDER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.register(PROVIDER_ID, request(1, 10)))
                .isInstanceOf(BusinessAlreadyRegisteredException.class);
        verify(businessRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_simultaneousRequests_uniqueConstraintIsReportedAsAlreadyRegistered() {
        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.APPROVED));
        when(businessRepository.existsByProviderId(PROVIDER_ID)).thenReturn(false);
        when(localityRepository.findById(10)).thenReturn(Optional.of(laPaloma));
        when(businessRepository.saveAndFlush(any(Business.class)))
                .thenThrow(new DataIntegrityViolationException("business_provider_id_key"));

        assertThatThrownBy(() -> service.register(PROVIDER_ID, request(1, 10)))
                .isInstanceOf(BusinessAlreadyRegisteredException.class);
    }

    @Test
    void register_unknownLocality_throwsInvalidLocation() {
        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.APPROVED));
        when(localityRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(PROVIDER_ID, request(1, 99)))
                .isInstanceOf(InvalidLocationException.class);
    }

    @Test
    void register_localityFromAnotherDepartment_throwsInvalidLocation() {
        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.APPROVED));
        when(localityRepository.findById(10)).thenReturn(Optional.of(laPaloma));

        // La Paloma es de Rocha, no de Maldonado
        assertThatThrownBy(() -> service.register(PROVIDER_ID, request(maldonado.getId(), 10)))
                .isInstanceOf(InvalidLocationException.class);
        verify(businessRepository, never()).saveAndFlush(any());
    }

    // ---------- formDefaults ----------

    @Test
    void formDefaults_legalPerson_prefillsBusinessName() {
        // Paso 2: si se registró como persona jurídica, el nombre aparece precargado
        providerExists(provider(PersonType.LEGAL, ProviderStatus.APPROVED));

        RegisterBusinessRequest form = service.formDefaults(PROVIDER_ID);

        assertThat(form.getName()).isEqualTo("Kayak Laguna de Rocha");
        assertThat(form.getEmail()).isEqualTo("juan@mail.com");
        assertThat(form.getPhone()).isEqualTo("099111222");
    }

    @Test
    void formDefaults_physicalPerson_leavesNameEmpty() {
        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.APPROVED));

        RegisterBusinessRequest form = service.formDefaults(PROVIDER_ID);

        assertThat(form.getName()).isNull();
        assertThat(form.getEmail()).isEqualTo("juan@mail.com");
    }

    // ---------- Consultas ----------

    @Test
    void isApproved_dependsOnProviderStatus() {
        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.APPROVED));
        assertThat(service.isApproved(PROVIDER_ID)).isTrue();

        providerExists(provider(PersonType.PHYSICAL, ProviderStatus.PENDING));
        assertThat(service.isApproved(PROVIDER_ID)).isFalse();
    }

    @Test
    void findForProvider_returnsSummaryWhenRegistered() {
        Business business = Business.builder().id(5L).name("Kayak").locality(laPaloma)
                .address("Ruta 10").phone("099").email("a@b.uy").description("Salidas").build();
        when(businessRepository.findByProviderId(PROVIDER_ID)).thenReturn(Optional.of(business));

        assertThat(service.findForProvider(PROVIDER_ID))
                .hasValueSatisfying(s -> assertThat(s.departmentName()).isEqualTo("Rocha"));
    }

    @Test
    void locationOptions_includesDepartmentOfEachLocality() {
        when(departmentRepository.findAllByOrderByNameAsc()).thenReturn(List.of(maldonado, rocha));
        when(localityRepository.findAllWithDepartment()).thenReturn(List.of(laPaloma));

        var options = service.locationOptions();

        assertThat(options.departments()).extracting("name").containsExactly("Maldonado", "Rocha");
        assertThat(options.localities()).singleElement()
                .satisfies(l -> assertThat(l.departmentId()).isEqualTo(1));
    }

    @Test
    void summaryLocation_sameNameAsDepartment_showsItOnce() {
        Department montevideo = Department.builder().id(3).name("Montevideo").build();
        Locality capital = Locality.builder().id(30).department(montevideo).name("Montevideo").build();
        Business business = Business.builder().id(6L).name("City tour").locality(capital).build();

        assertThat(BusinessSummary.from(business).location()).isEqualTo("Montevideo");
    }
}
