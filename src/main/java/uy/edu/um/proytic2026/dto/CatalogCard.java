package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Una actividad en la vidriera pública (CU-05 paso 2): su resumen y la calificación promedio.
 *
 * @param averageRating promedio de las valoraciones, o null si todavía no tiene.
 * @param nextDate      la próxima fecha con lugar, o null si no tiene fechas próximas.
 */
public record CatalogCard(Long id, String name, String businessName, String localityName, String departmentName,
                          List<String> typeNames, BigDecimal price, int durationMinutes, int minAge,
                          Double averageRating, long reviewCount, LocalDateTime nextDate) {

    private static final Locale ES_UY = Locale.forLanguageTag("es-UY");

    public String location() {
        return localityName.equalsIgnoreCase(departmentName) ? departmentName : localityName + ", " + departmentName;
    }

    /** El paisaje que ilustra la tarjeta, según su primer tipo (scenes.js). */
    public String scene() {
        return HomeOverview.sceneFor(typeNames.isEmpty() ? "" : typeNames.get(0));
    }

    /** El tipo que va en la etiqueta de la foto. */
    public String mainType() {
        return typeNames.isEmpty() ? "Actividad" : typeNames.get(0);
    }

    public String typesLabel() {
        return String.join(" · ", typeNames);
    }

    public String priceLabel() {
        boolean whole = price.stripTrailingZeros().scale() <= 0;
        return "$ " + new DecimalFormat(whole ? "#,##0" : "#,##0.00", DecimalFormatSymbols.getInstance(ES_UY)).format(price);
    }

    public String durationLabel() {
        return ActivitySummary.formatMinutes(durationMinutes);
    }

    public boolean rated() {
        return averageRating != null;
    }

    /** "4,5". */
    public String ratingLabel() {
        return rated() ? new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(ES_UY)).format(averageRating) : "";
    }

    /** "1 valoración", "12 valoraciones" o "Sin valoraciones todavía". */
    public String reviewsLabel() {
        if (reviewCount == 0) return "Sin valoraciones todavía";
        return reviewCount + (reviewCount == 1 ? " valoración" : " valoraciones");
    }

    /** "Dom 25 oct · 10:00" o null. */
    public String nextDateLabel() {
        return nextDate == null ? null : DateLabels.shortDay(nextDate.toLocalDate()) + " · " + DateLabels.time(nextDate);
    }
}
