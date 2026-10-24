package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Todo lo que el visitante necesita para decidir (CU-06 pasos 2 a 5 y RF-03).
 *
 * @param equipmentProvided lo pone el prestador.
 * @param equipmentToBring  lo tiene que traer el turista ("equipamiento necesario").
 * @param dates             próximas fechas, de la más cercana a la más lejana.
 * @param averageRating     promedio de las valoraciones, o null si no tiene.
 */
public record ActivityDetail(Long id, String name, String description, String businessName,
                             String localityName, String departmentName, List<String> typeNames,
                             int durationMinutes, BigDecimal price, int minAge, int bookingCutoffMinutes,
                             List<String> equipmentProvided, List<String> equipmentToBring,
                             List<DetailDate> dates, List<ReviewView> reviews, Double averageRating) {

    private static final Locale ES_UY = Locale.forLanguageTag("es-UY");

    public ActivityDetail withDates(List<DetailDate> newDates) {
        return new ActivityDetail(id, name, description, businessName, localityName, departmentName, typeNames,
                durationMinutes, price, minAge, bookingCutoffMinutes, equipmentProvided, equipmentToBring,
                newDates, reviews, averageRating);
    }

    public String location() {
        return localityName.equalsIgnoreCase(departmentName) ? departmentName : localityName + ", " + departmentName;
    }

    public String typesLabel() {
        return String.join(" · ", typeNames);
    }

    public String durationLabel() {
        return ActivitySummary.formatMinutes(durationMinutes);
    }

    public String priceLabel() {
        boolean whole = price.stripTrailingZeros().scale() <= 0;
        return "$ " + new DecimalFormat(whole ? "#,##0" : "#,##0.00", DecimalFormatSymbols.getInstance(ES_UY)).format(price);
    }

    public String minAgeLabel() {
        return minAge == 0 ? "Todas las edades" : "Desde " + minAge + " años";
    }

    /** "Hasta la hora de comienzo" o "Hasta 60 min antes". */
    public String cutoffLabel() {
        return bookingCutoffMinutes == 0 ? "Hasta la hora de comienzo"
                : "Hasta " + ActivitySummary.formatMinutes(bookingCutoffMinutes) + " antes";
    }

    public boolean rated() {
        return averageRating != null;
    }

    public String ratingLabel() {
        return rated() ? new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(ES_UY)).format(averageRating) : "";
    }

    public String reviewsLabel() {
        int count = reviews.size();
        if (count == 0) return "Sin valoraciones todavía";
        return count + (count == 1 ? " valoración" : " valoraciones");
    }

    public boolean noEquipment() {
        return equipmentProvided.isEmpty() && equipmentToBring.isEmpty();
    }

    /** Si al menos una fecha tiene pronóstico (si no, se avisa una sola vez que no hay clima). */
    public boolean anyForecast() {
        return dates.stream().anyMatch(d -> d.forecast() != null);
    }

    public boolean anyAvailable() {
        return dates.stream().anyMatch(DetailDate::available);
    }
}
