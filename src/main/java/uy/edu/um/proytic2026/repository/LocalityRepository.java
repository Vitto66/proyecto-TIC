package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.proytic2026.entity.Locality;

import java.util.List;

public interface LocalityRepository extends JpaRepository<Locality, Integer> {

    /** Todas las localidades con su departamento ya cargado, para armar el selector del formulario. */
    @Query("select l from Locality l join fetch l.department order by l.name")
    List<Locality> findAllWithDepartment();
}
