package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.ActivityInstance;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityInstanceRepository extends JpaRepository<ActivityInstance, Long> {

    List<ActivityInstance> findByActivityIdOrderByStartAtAsc(Long activityId);

    boolean existsByActivityIdAndStartAt(Long activityId, LocalDateTime startAt);

    /** Las instancias de una actividad que empiezan dentro de una ventana (ambos extremos incluidos). */
    List<ActivityInstance> findByActivityIdAndStartAtBetweenOrderByStartAtAsc(
            Long activityId, LocalDateTime from, LocalDateTime to);
}
