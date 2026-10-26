package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.Review;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Promedio y cantidad de valoraciones de una actividad. */
    interface RatingStats {
        Long getActivityId();

        Double getAverage();

        Long getCount();
    }

    /** Las estadísticas de todas las actividades que tienen al menos una valoración. */
    @Query("""
            select i.activity.id as activityId, avg(r.rating) as average, count(r) as count
            from Review r join r.reservation res join res.instance i
            group by i.activity.id
            """)
    List<RatingStats> ratingStats();

    /** Las valoraciones de una actividad, de la más nueva a la más vieja (CU-06). */
    @Query("""
            select r from Review r
            join fetch r.reservation res
            join fetch res.tourist
            where res.instance.activity.id = :activityId
            order by r.createdAt desc
            """)
    List<Review> findByActivityId(@Param("activityId") Long activityId);

    /** CU-17 4b: cuáles de esas reservas ya tienen su valoración (hay una sola por reserva). */
    @Query("select r.reservation.id from Review r where r.reservation.id in :reservationIds")
    List<Long> findReviewedReservationIds(@Param("reservationIds") java.util.Collection<Long> reservationIds);

    /** CU-18: la valoración de una reserva, si ya la tiene. */
    java.util.Optional<Review> findByReservationId(Long reservationId);

    /** CU-18 1a: si esa reserva ya fue valorada. */
    boolean existsByReservationId(Long reservationId);

    /** CU-24: las valoraciones que hizo un turista, de la más nueva a la más vieja, con su actividad. */
    @Query("""
            select r from Review r
            join fetch r.reservation res
            join fetch res.instance i
            join fetch i.activity a
            join fetch a.business
            where res.tourist.id = :touristId
            order by r.createdAt desc, r.id desc
            """)
    List<Review> findAllOfTourist(@Param("touristId") Long touristId);
}
