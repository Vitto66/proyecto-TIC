package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.ActivityType;

import java.util.List;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Integer> {

    /** Los tipos que se ofrecen al publicar una actividad. */
    List<ActivityType> findByActiveTrueOrderByNameAsc();
}
