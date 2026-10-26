package uy.edu.um.proytic2026.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementación actual de {@link TouristNotifier}: deja constancia en el log. El turista se
 * entera dentro de la plataforma al ver su reserva. Para mandar un correo, reemplazar esta clase
 * por una que use spring-boot-starter-mail.
 */
@Slf4j
@Component
public class LoggingTouristNotifier implements TouristNotifier {

    @Override
    public void reservationCancelled(String email, String touristName, String activityName, Long reservationId,
                                     PaymentService.RefundOutcome refundStatus) {
        log.info("Avisar a {} ({}): se canceló su reserva {} de \"{}\" porque se dio de baja la actividad. Devolución: {}",
                touristName, email, reservationId, activityName, refundStatus);
    }
}
