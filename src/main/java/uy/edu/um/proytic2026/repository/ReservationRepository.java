package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;

import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Lugares ocupados de una instancia: la suma de participantes de sus reservas en esos estados. */
    interface OccupiedSeats {
        Long getInstanceId();

        Long getSeats();
    }

    @Query("""
            select r.instance.id as instanceId, sum(r.participants) as seats
            from Reservation r
            where r.instance.id in :instanceIds and r.status in :statuses
            group by r.instance.id
            """)
    List<OccupiedSeats> occupiedSeats(@Param("instanceIds") Collection<Long> instanceIds,
                                      @Param("statuses") Collection<ReservationStatus> statuses);

    /** CU-11: cantidad de reservas de cada instancia, en esos estados. */
    interface ReservationCount {
        Long getInstanceId();

        Long getReservations();
    }

    @Query("""
            select r.instance.id as instanceId, count(r) as reservations
            from Reservation r
            where r.instance.id in :instanceIds and r.status in :statuses
            group by r.instance.id
            """)
    List<ReservationCount> countByInstance(@Param("instanceIds") Collection<Long> instanceIds,
                                           @Param("statuses") Collection<ReservationStatus> statuses);

    /** CU-11 paso 4: las reservas de una instancia, con el turista y sus acompañantes ya cargados. */
    @Query("""
            select distinct r from Reservation r
            join fetch r.tourist t
            join fetch t.user
            left join fetch r.companions
            where r.instance.id = :instanceId
            order by r.createdAt
            """)
    List<Reservation> findAllForInstance(@Param("instanceId") Long instanceId);

    /**
     * Las reservas de una actividad en fechas que todavía no empezaron, en esos estados
     * (CU-10: las que se cancelan al darla de baja).
     */
    @Query("""
            select r from Reservation r
            join fetch r.instance i
            join fetch r.tourist t
            join fetch t.user
            where i.activity.id = :activityId and i.startAt > :after and r.status in :statuses
            order by i.startAt, r.id
            """)
    List<Reservation> findUpcomingByActivity(@Param("activityId") Long activityId,
                                             @Param("after") java.time.LocalDateTime after,
                                             @Param("statuses") Collection<ReservationStatus> statuses);
}
