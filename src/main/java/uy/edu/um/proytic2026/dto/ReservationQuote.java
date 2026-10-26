package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * CU-14 paso 5: el resumen de la reserva antes de confirmar, con el precio total.
 *
 * @param unitPrice  precio por persona de la actividad en este momento.
 * @param companions datos de las personas 2 en adelante (la 1 es el turista).
 */
public record ReservationQuote(Long activityId, String activityName, String businessName, String location,
                               ActivityDateView date, int participants, BigDecimal unitPrice,
                               List<CompanionForm> companions) {

    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(participants));
    }

    public String unitPriceLabel() {
        return Money.format(unitPrice);
    }

    public String totalLabel() {
        return Money.format(total());
    }

    /** "2 personas" o "1 persona". */
    public String participantsLabel() {
        return participants + (participants == 1 ? " persona" : " personas");
    }
}
