package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;

import java.util.List;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    List<Provider> findByStatus(ProviderStatus status);

    /** El id es el de la cuenta, que se genera en orden: así los más antiguos quedan primero. */
    List<Provider> findByStatusOrderByIdAsc(ProviderStatus status);

    long countByStatus(ProviderStatus status);

    boolean existsByDocument(String document);

    boolean existsByRut(String rut);
}
