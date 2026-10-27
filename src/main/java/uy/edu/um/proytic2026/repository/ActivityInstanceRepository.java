package uy.edu.um.proytic2026.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.ActivityInstance;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ActivityInstanceRepository extends JpaRepository<ActivityInstance, Long> {

    List<ActivityInstance> findByActivityIdOrderByStartAtAsc(Long activityId);

    boolean existsByActivityIdAndStartAt(Long activityId, LocalDateTime startAt);

    /** Las instancias de una actividad que empiezan dentro de una ventana (ambos extremos incluidos). */
    List<ActivityInstance> findByActivityIdAndStartAtBetweenOrderByStartAtAsc(
            Long activityId, LocalDateTime from, LocalDateTime to);

    /** Las instancias de esas actividades que empiezan después de un momento, de la más cercana a la más lejana. */
    @Query("""
            select i from ActivityInstance i
            where i.activity.id in :activityIds and i.startAt > :after
            order by i.startAt
            """)
    List<ActivityInstance> findUpcoming(@Param("activityIds") Collection<Long> activityIds,
                                        @Param("after") LocalDateTime after);

    /**
     * La instancia bloqueada hasta que termine la transacción (CU-15): si dos turistas pagan los
     * últimos lugares a la vez, el segundo espera y ve los cupos ya descontados.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ActivityInstance i where i.id = :id")
    java.util.Optional<ActivityInstance> findLockedById(@Param("id") Long id);

    /** CU-11: todas las fechas de las actividades de un prestador, con la actividad ya cargada. */
    @Query("""
            select i from ActivityInstance i
            join fetch i.activity a
            where a.business.provider.id = :providerId
            order by i.startAt
            """)
    List<ActivityInstance> findAllByProviderId(@Param("providerId") Long providerId);

    /**
     * CU-20: las fechas de actividades publicadas de un departamento que empiezan entre from
     * (incluido) y to (excluido), con la actividad, el emprendimiento y la ubicación ya cargados.
     */
    @Query("""
            select i from ActivityInstance i
            join fetch i.activity a
            join fetch a.business
            join fetch a.locality l
            join fetch l.department d
            where a.active = true and d.id = :departmentId
              and i.startAt >= :from and i.startAt < :to
            order by i.startAt, a.name
            """)
    List<ActivityInstance> findPublishedInDepartment(@Param("departmentId") Integer departmentId,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);

    /** CU-20: lo mismo, pero de una localidad. */
    @Query("""
            select i from ActivityInstance i
            join fetch i.activity a
            join fetch a.business
            join fetch a.locality l
            join fetch l.department
            where a.active = true and l.id = :localityId
              and i.startAt >= :from and i.startAt < :to
            order by i.startAt, a.name
            """)
    List<ActivityInstance> findPublishedInLocality(@Param("localityId") Integer localityId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);
}
