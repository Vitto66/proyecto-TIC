package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ProviderReview;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.ProviderAlreadyReviewedException;
import uy.edu.um.proytic2026.exception.ProviderNotFoundException;
import uy.edu.um.proytic2026.repository.ProviderRepository;

import java.util.List;

/**
 * CU-03 Aprobar o rechazar un prestador.
 * <p>
 * Un prestador APROBADO puede registrar su emprendimiento y publicar actividades; uno
 * RECHAZADO no puede ingresar a su espacio de gestión (CU-04 2b). Solo se resuelven
 * prestadores PENDIENTES: una vez resuelto, el estado no se cambia desde acá.
 */
@Service
@RequiredArgsConstructor
public class ProviderReviewService {

    private final ProviderRepository providerRepository;
    private final ProviderNotifier notifier;

    /** Paso 1: la lista de prestadores pendientes, del más antiguo al más nuevo. */
    @Transactional(readOnly = true)
    public List<ProviderReview> pendingProviders() {
        return providerRepository.findByStatusOrderByIdAsc(ProviderStatus.PENDING).stream()
                .map(ProviderReview::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return providerRepository.countByStatus(ProviderStatus.PENDING);
    }

    /** Paso 2: el detalle de un prestador para revisar sus datos. */
    @Transactional(readOnly = true)
    public ProviderReview detail(Long providerId) {
        return ProviderReview.from(find(providerId));
    }

    /** Paso 3 y 4: aprobar. El prestador queda habilitado para registrar su emprendimiento. */
    @Transactional
    public ProviderReview approve(Long providerId) {
        return resolve(providerId, ProviderStatus.APPROVED);
    }

    /** 3a: rechazar. El prestador no puede publicar ni ingresar a su espacio de gestión. */
    @Transactional
    public ProviderReview reject(Long providerId) {
        return resolve(providerId, ProviderStatus.REJECTED);
    }

    private ProviderReview resolve(Long providerId, ProviderStatus newStatus) {
        Provider provider = find(providerId);
        if (provider.getStatus() != ProviderStatus.PENDING) {
            throw new ProviderAlreadyReviewedException();
        }
        provider.setStatus(newStatus);
        notifier.providerReviewed(provider);
        return ProviderReview.from(provider);
    }

    private Provider find(Long providerId) {
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
    }
}
