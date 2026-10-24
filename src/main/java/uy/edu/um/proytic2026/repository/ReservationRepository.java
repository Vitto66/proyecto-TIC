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
}
