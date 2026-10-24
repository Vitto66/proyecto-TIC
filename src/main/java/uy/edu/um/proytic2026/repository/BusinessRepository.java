package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.Business;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    /** providerId es el id del prestador, que coincide con el id de su cuenta (user_id). */
    Optional<Business> findByProviderId(Long providerId);

    boolean existsByProviderId(Long providerId);
}
