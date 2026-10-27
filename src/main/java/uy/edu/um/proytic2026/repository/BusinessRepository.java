package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.ProviderStatus;

import java.util.List;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    /** providerId es el id del prestador, que coincide con el id de su cuenta (user_id). */
    Optional<Business> findByProviderId(Long providerId);

    boolean existsByProviderId(Long providerId);

    /**
     * Emprendimientos cuyo prestador tiene ese estado, con la localidad y el departamento ya
     * cargados, ordenados por nombre (API, CU-19).
     */
    @Query("""
            select b from Business b
            join fetch b.locality l
            join fetch l.department
            where b.provider.status = :status
            order by b.name""")
    List<Business> findAllByProviderStatus(@Param("status") ProviderStatus status);
}
