package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.ActivityType;

import java.util.List;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Integer> {

    /** Los tipos que se ofrecen al publicar una actividad. */
    List<ActivityType> findByActiveTrueOrderByNameAsc();

    /** CU-13: la lista completa para el administrador, activos e inactivos. */
    List<ActivityType> findAllByOrderByNameAsc();

    /** CU-13 3b: nombre repetido, sin distinguir mayúsculas. */
    boolean existsByNameIgnoreCase(String name);

    /** CU-13 3b al modificar: otro tipo (no este) ya tiene ese nombre. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);
}
