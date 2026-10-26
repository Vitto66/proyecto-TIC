package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.EquipmentType;

import java.util.List;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Integer> {

    /** Los tipos de equipamiento que se ofrecen al publicar una actividad. */
    List<EquipmentType> findByActiveTrueOrderByNameAsc();

    /** CU-13: la lista completa para el administrador, activos e inactivos. */
    List<EquipmentType> findAllByOrderByNameAsc();

    /** CU-13 3b: nombre repetido, sin distinguir mayúsculas. */
    boolean existsByNameIgnoreCase(String name);

    /** CU-13 3b al modificar: otro tipo (no este) ya tiene ese nombre. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);
}
