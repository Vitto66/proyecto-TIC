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
}
