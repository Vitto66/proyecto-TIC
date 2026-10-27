package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.PublicBusiness;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.repository.BusinessRepository;

import java.util.List;

/**
 * Consultas de la API para otras plataformas (RF-10). Son todas de solo lectura y no
 * necesitan un usuario de la plataforma.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicApiService {

    private final BusinessRepository businessRepository;

    /**
     * CU-19: todos los emprendimientos de prestadores aprobados. Los de prestadores pendientes
     * o rechazados no se publican. Si no hay ninguno, la lista vuelve vacía (2a).
     */
    public List<PublicBusiness> listBusinesses() {
        return businessRepository.findAllByProviderStatus(ProviderStatus.APPROVED).stream()
                .map(PublicBusiness::from)
                .toList();
    }
}
