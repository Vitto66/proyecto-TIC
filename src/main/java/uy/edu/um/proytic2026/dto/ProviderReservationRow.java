package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.ReservationStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * CU-11 paso 4: una reserva de la fecha elegida, como la ve el prestador: quién reservó, cuántas
 * personas van, sus acompañantes y el estado.
 */
public record ProviderReservationRow(Long id, String touristName, String nationalId, String email, String phone,
                                     int participants, BigDecimal totalPrice, ReservationStatus status,
                                     List<CompanionView> companions) {

    public String participantsLabel() {
        return participants + (participants == 1 ? " persona" : " personas");
    }

    public String totalLabel() {
        return Money.format(totalPrice);
    }

    public String statusLabel() {
        return switch (status) {
            case PENDING -> "Pendiente de pago";
            case CONFIRMED -> "Confirmada";
            case FINISHED -> "Finalizada";
            case EXPIRED -> "Vencida";
        };
    }

    /** Clase CSS del indicador de estado. */
    public String statusClass() {
        return switch (status) {
            case PENDING -> "status-pending";
            case CONFIRMED, FINISHED -> "status-approved";
            case EXPIRED -> "status-rejected";
        };
    }

    /** Solo las confirmadas y finalizadas ocupan lugar (CU-14, CU-15). */
    public boolean takesSeats() {
        return status == ReservationStatus.CONFIRMED || status == ReservationStatus.FINISHED;
    }
}
