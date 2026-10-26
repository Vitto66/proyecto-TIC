package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.PaymentStatus;
import uy.edu.um.proytic2026.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una reserva del turista para mostrar en pantalla.
 *
 * @param touristName  la persona 1, el turista que reservó.
 * @param companions   las personas 2 en adelante.
 * @param lastPayment  el resultado del último intento de pago, o null si todavía no intentó pagar.
 * @param payable      si todavía se puede pagar: está pendiente, no pasaron 24 horas y no cerraron las reservas.
 */
public record ReservationView(Long id, Long activityId, String activityName, String businessName, String location,
                              ActivityDateView date, int participants, BigDecimal totalPrice,
                              ReservationStatus status, LocalDateTime createdAt, String touristName,
                              List<CompanionView> companions, PaymentStatus lastPayment, boolean payable) {

    /** Una reserva PENDIENTE vence a las 24 horas de creada si no se paga (CU-15 4a, CU-16). */
    public static final int HOURS_TO_PAY = 24;

    public String totalLabel() {
        return Money.format(totalPrice);
    }

    public String participantsLabel() {
        return participants + (participants == 1 ? " persona" : " personas");
    }

    public boolean pending() {
        return status == ReservationStatus.PENDING;
    }

    public boolean confirmed() {
        return status == ReservationStatus.CONFIRMED;
    }

    /** El último pago quedó en revisión del servicio: se puede consultar si ya se aprobó. */
    public boolean paymentInProcess() {
        return pending() && lastPayment == PaymentStatus.IN_PROCESS;
    }

    /** Hasta cuándo se puede pagar: "Martes 27 de octubre de 2026 a las 10:15". */
    public String payUntilLabel() {
        LocalDateTime until = createdAt.plusHours(HOURS_TO_PAY);
        return DateLabels.day(until.toLocalDate()) + " a las " + DateLabels.time(until);
    }

    public String statusLabel() {
        return switch (status) {
            case PENDING -> "Pendiente de pago";
            case CONFIRMED -> "Confirmada";
            case FINISHED -> "Finalizada";
            case EXPIRED -> "Vencida";
        };
    }
}
