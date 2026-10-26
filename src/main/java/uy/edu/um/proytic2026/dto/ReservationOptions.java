package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que necesita el formulario del CU-14: la actividad y sus próximas fechas, con lugares y
 * si todavía se puede reservar cada una.
 */
public record ReservationOptions(Long activityId, String activityName, String businessName, String location,
                                 BigDecimal price, List<DetailDate> dates) {

    public String priceLabel() {
        return Money.format(price);
    }

    public boolean anyAvailable() {
        return dates.stream().anyMatch(DetailDate::available);
    }
}
