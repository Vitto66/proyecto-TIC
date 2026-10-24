package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.Activity;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /**
     * Las actividades del emprendimiento de un prestador ("Mis actividades"), con la ubicación
     * y los tipos ya cargados para mostrarlas sin consultas extra.
     */
    @Query("""
            select distinct a from Activity a
            join fetch a.locality l
            join fetch l.department
            left join fetch a.types
            where a.business.provider.id = :providerId
            order by a.name
            """)
    List<Activity> findAllByProviderId(@Param("providerId") Long providerId);

    /**
     * Las actividades publicadas, para la vidriera pública (CU-05), con el emprendimiento, la
     * ubicación y los tipos ya cargados.
     */
    @Query("""
            select distinct a from Activity a
            join fetch a.business
            join fetch a.locality l
            join fetch l.department
            left join fetch a.types
            where a.active = true
            """)
    List<Activity> findAllPublished();
}
