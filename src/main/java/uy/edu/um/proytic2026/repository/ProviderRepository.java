package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;

import java.util.List;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    List<Provider> findByStatus(ProviderStatus status);

    boolean existsByDocument(String document);

    boolean existsByRut(String rut);
}
