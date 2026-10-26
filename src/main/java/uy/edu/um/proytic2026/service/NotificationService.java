package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.DateLabels;
import uy.edu.um.proytic2026.dto.NotificationView;
import uy.edu.um.proytic2026.entity.TouristNotification;
import uy.edu.um.proytic2026.repository.TouristNotificationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Los avisos del turista (por ahora, las reservas que vencieron en el CU-16). */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final TouristNotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<NotificationView> unread(Long touristId) {
        return notificationRepository.findByTouristIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(touristId).stream()
                .map(NotificationService::view)
                .toList();
    }

    /** El turista ya los vio: dejan de aparecer en su espacio. */
    @Transactional
    public int markAllRead(Long touristId) {
        return notificationRepository.markAllRead(touristId, LocalDateTime.now(clock));
    }

    private static NotificationView view(TouristNotification n) {
        Long reservationId = n.getReservation() == null ? null : n.getReservation().getId();
        String created = DateLabels.shortDay(n.getCreatedAt().toLocalDate()) + ", " + DateLabels.time(n.getCreatedAt());
        return new NotificationView(n.getId(), n.getMessage(), reservationId, created);
    }
}
