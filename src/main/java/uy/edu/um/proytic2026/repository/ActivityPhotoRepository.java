package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.proytic2026.entity.ActivityPhoto;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityPhotoRepository extends JpaRepository<ActivityPhoto, Long> {

    /** Qué actividades tienen foto y cuándo se subió, sin traer los bytes de las imágenes. */
    @Query("select p.activityId as activityId, p.updatedAt as updatedAt, p.externalUrl as externalUrl from ActivityPhoto p")
    List<PhotoStamp> stamps();

    interface PhotoStamp {
        Long getActivityId();

        LocalDateTime getUpdatedAt();

        String getExternalUrl();
    }
}
