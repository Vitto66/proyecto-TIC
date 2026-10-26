package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CU-16 paso 1: "el sistema revisa periódicamente las reservas". Por defecto cada 5 minutos
 * (app.reservations.status-job.every-ms). Se apaga con app.reservations.status-job.enabled=false;
 * los tests lo apagan para que no cambie datos mientras corren.
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reservations.status-job.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationStatusJob {

    private final ReservationStatusService statusService;

    @Scheduled(initialDelayString = "${app.reservations.status-job.initial-delay-ms:30000}",
            fixedDelayString = "${app.reservations.status-job.every-ms:300000}")
    public void run() {
        statusService.update();
    }
}
