package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.EquipmentType;

import java.util.List;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Integer> {

    /** Los tipos de equipamiento que se ofrecen al publicar una actividad. */
    List<EquipmentType> findByActiveTrueOrderByNameAsc();
}
