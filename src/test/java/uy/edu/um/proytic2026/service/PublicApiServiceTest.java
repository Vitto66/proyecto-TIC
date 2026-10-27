package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.dto.PublicBusiness;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.repository.BusinessRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Tests unitarios de la API para otras plataformas (RF-10). */
@ExtendWith(MockitoExtension.class)
class PublicApiServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    private PublicApiService service;

    private final Department rocha = Department.builder().id(1).name("Rocha").build();
    private final Locality laPaloma = Locality.builder().id(10).department(rocha).name("La Paloma").build();

    @BeforeEach
    void setUp() {
        service = new PublicApiService(businessRepository);
    }

    // ---------- CU-19 Listar emprendimientos ----------

    @Test
    void listBusinesses_returnsPublicDataOfApprovedOnes() {
        Business kayak = Business.builder().id(3L).locality(laPaloma).name("Kayak Laguna de Rocha")
                .description("Salidas en kayak").address("Ruta 10 km 264")
                .phone("099123456").email("info@laguna.uy").build();
        when(businessRepository.findAllByProviderStatus(ProviderStatus.APPROVED)).thenReturn(List.of(kayak));

        List<PublicBusiness> result = service.listBusinesses();

        assertThat(result).containsExactly(new PublicBusiness(3L, "Kayak Laguna de Rocha", "Salidas en kayak",
                "Ruta 10 km 264", "099123456", "info@laguna.uy", "La Paloma", "Rocha"));
    }

    @Test
    void listBusinesses_noneRegistered_returnsEmptyList() {
        when(businessRepository.findAllByProviderStatus(ProviderStatus.APPROVED)).thenReturn(List.of());

        assertThat(service.listBusinesses()).isEmpty();
    }
}
