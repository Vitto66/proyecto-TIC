package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /** Los ids de las reservas en un estado (CU-16 las revisa de a una). */
    @Query("select r.id from Reservation r where r.status = :status order by r.id")
    List<Long> findIdsByStatus(@Param("status") ReservationStatus status);

    /** La fecha (instancia) de una reserva, sin cargarla: para bloquear la fecha antes de leer la reserva. */
    @Query("select r.instance.id from Reservation r where r.id = :id")
    Optional<Long> findInstanceIdById(@Param("id") Long id);

    /**
     * El estado actual en la base. Se consulta después de bloquear la fecha para no decidir con un
     * estado viejo si otra transacción (el pago o el vencimiento) lo cambió mientras tanto.
     */
    @Query("select r.status from Reservation r where r.id = :id")
    Optional<ReservationStatus> findStatusById(@Param("id") Long id);

    /** Las reservas confirmadas de fechas que ya empezaron, con su actividad (CU-16 paso 5). */
    @Query("""
            select r from Reservation r join fetch r.instance i join fetch i.activity
            where r.status = uy.edu.um.proytic2026.entity.ReservationStatus.CONFIRMED and i.startAt <= :now
            """)
    List<Reservation> findConfirmedStartedBy(@Param("now") LocalDateTime now);

    /**
     * CU-17: todas las reservas de un turista, con la actividad, el emprendimiento y los tipos ya
     * cargados, de la fecha más cercana a la más lejana.
     */
    @Query("""
            select distinct r from Reservation r
            join fetch r.instance i
            join fetch i.activity a
            join fetch a.business
            left join fetch a.types
            where r.tourist.id = :touristId
            order by i.startAt, r.id
            """)
    List<Reservation> findAllOfTourist(@Param("touristId") Long touristId);

    /** CU-24 paso 2: las reservas FINALIZADAS del turista que todavía no tienen valoración. */
    @Query("""
            select r from Reservation r
            join fetch r.instance i
            join fetch i.activity a
            join fetch a.business
            where r.tourist.id = :touristId
              and r.status = uy.edu.um.proytic2026.entity.ReservationStatus.FINISHED
              and not exists (select 1 from Review rv where rv.reservation = r)
            order by i.startAt desc, r.id desc
            """)
    List<Reservation> findFinishedWithoutReview(@Param("touristId") Long touristId);

    /** Cuántas reservas en esos estados recibió cada actividad desde un momento (el inicio). Filas [idActividad, cantidad]. */
    @Query("""
            select r.instance.activity.id, count(r) from Reservation r
            where r.createdAt >= :since and r.status in :statuses
            group by r.instance.activity.id
            """)
    List<Object[]> countBookingsSince(@Param("since") java.time.LocalDateTime since,
                                      @Param("statuses") Collection<ReservationStatus> statuses);
}
