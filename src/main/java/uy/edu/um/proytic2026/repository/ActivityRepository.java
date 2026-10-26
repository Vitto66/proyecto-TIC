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

    /** CU-13: cuántas actividades usa cada tipo de actividad. Filas [idDelTipo, cantidad]. */
    @Query("select t.id, count(a) from Activity a join a.types t group by t.id")
    List<Object[]> countByActivityType();

    /** CU-13: cuántas actividades usa cada tipo de equipamiento. Filas [idDelTipo, cantidad]. */
    @Query("select e.equipmentType.id, count(e) from ActivityEquipment e group by e.equipmentType.id")
    List<Object[]> countByEquipmentType();

    /** CU-13 3a: cuántas actividades usan un tipo de actividad. */
    @Query("select count(a) from Activity a join a.types t where t.id = :typeId")
    long countUsingActivityType(@Param("typeId") Integer typeId);

    /** CU-13 3a: cuántas actividades usan un tipo de equipamiento. */
    @Query("select count(e) from ActivityEquipment e where e.equipmentType.id = :typeId")
    long countUsingEquipmentType(@Param("typeId") Integer typeId);
}
