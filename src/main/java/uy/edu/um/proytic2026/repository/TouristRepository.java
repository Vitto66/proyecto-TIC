package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.Tourist;

public interface TouristRepository extends JpaRepository<Tourist, Long> {

    boolean existsByNationalId(String nationalId);
}
