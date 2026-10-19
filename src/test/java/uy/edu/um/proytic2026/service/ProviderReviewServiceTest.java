package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.dto.ProviderReview;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.exception.ProviderAlreadyReviewedException;
import uy.edu.um.proytic2026.exception.ProviderNotFoundException;
import uy.edu.um.proytic2026.repository.ProviderRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests unitarios del CU-03 Aprobar o rechazar un prestador. */
@ExtendWith(MockitoExtension.class)
class ProviderReviewServiceTest {

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private ProviderNotifier notifier;

    private ProviderReviewService service;

    @BeforeEach
    void setUp() {
        service = new ProviderReviewService(providerRepository, notifier);
    }

    private Provider provider(long id, ProviderStatus status) {
        User user = User.builder().id(id).email("prestador" + id + "@mail.com").passwordHash("x").build();
        Provider provider = Provider.builder().id(id).user(user).personType(PersonType.PHYSICAL)
                .status(status).firstName("Juan").lastName("Rodríguez").document("4567890" + id)
                .address("Ruta 10 km 264").build();
        user.setProvider(provider);
        return provider;
    }

    @Test
    void pendingProviders_returnsReviewDataIncludingEmail() {
        when(providerRepository.findByStatusOrderByIdAsc(ProviderStatus.PENDING))
                .thenReturn(List.of(provider(1, ProviderStatus.PENDING)));

        List<ProviderReview> result = service.pendingProviders();

        assertThat(result).singleElement().satisfies(p -> {
            assertThat(p.displayName()).isEqualTo("Juan Rodríguez");
            assertThat(p.email()).isEqualTo("prestador1@mail.com");
            assertThat(p.identifierLabel()).isEqualTo("Cédula");
        });
    }

    @Test
    void approve_pendingProvider_changesStatusAndNotifies() {
        Provider pending = provider(1, ProviderStatus.PENDING);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(pending));

        ProviderReview result = service.approve(1L);

        assertThat(pending.getStatus()).isEqualTo(ProviderStatus.APPROVED);
        assertThat(result.status()).isEqualTo(ProviderStatus.APPROVED);
        verify(notifier).providerReviewed(pending);
    }

    @Test
    void reject_pendingProvider_changesStatusAndNotifies() {
        Provider pending = provider(1, ProviderStatus.PENDING);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(pending));

        service.reject(1L);

        assertThat(pending.getStatus()).isEqualTo(ProviderStatus.REJECTED);
        verify(notifier).providerReviewed(pending);
    }

    @Test
    void approve_alreadyReviewedProvider_isRefusedWithoutChanges() {
        Provider rejected = provider(1, ProviderStatus.REJECTED);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.approve(1L)).isInstanceOf(ProviderAlreadyReviewedException.class);
        assertThat(rejected.getStatus()).isEqualTo(ProviderStatus.REJECTED);
        verify(notifier, never()).providerReviewed(any());
    }

    @Test
    void approve_unknownProvider_throwsNotFound() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L)).isInstanceOf(ProviderNotFoundException.class);
        verify(notifier, never()).providerReviewed(any());
    }
}
