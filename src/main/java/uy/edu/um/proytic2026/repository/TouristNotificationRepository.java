package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.TouristNotification;

import java.time.LocalDateTime;
import java.util.List;

public interface TouristNotificationRepository extends JpaRepository<TouristNotification, Long> {

    /** Los avisos que el turista todavía no vio, del más nuevo al más viejo. */
    List<TouristNotification> findByTouristIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(Long touristId);

    /** Marca como vistos todos los avisos pendientes del turista. Devuelve cuántos marcó. */
    @Modifying
    @Query("""
            update TouristNotification n set n.readAt = :now
            where n.tourist.id = :touristId and n.readAt is null
            """)
    int markAllRead(@Param("touristId") Long touristId, @Param("now") LocalDateTime now);
}
