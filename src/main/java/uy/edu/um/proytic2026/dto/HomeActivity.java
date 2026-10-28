package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Una actividad publicada como se muestra en el inicio: en el panel del departamento elegido
 * en el mapa y en "Lo más reservado este mes".
 *
 * @param typeName      el primer tipo de la actividad (por nombre), para la etiqueta de la tarjeta.
 * @param scene         qué paisaje ilustra la tarjeta (sierra, rio, costa, campo, vino, palmar).
 * @param averageRating promedio de las valoraciones, o null si todavía no tiene.
 * @param monthBookings reservas pagas que recibió en el mes.
 */
public record HomeActivity(Long id, String name, String location, String typeName, String scene,
                           BigDecimal price, int durationMinutes, Double averageRating, long reviewCount,
                           long monthBookings) {

    private static final DecimalFormatSymbols ES_UY = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-UY"));

    public String priceLabel() {
        return Money.format(price);
    }

    public String durationLabel() {
        return ActivitySummary.formatMinutes(durationMinutes);
    }

    public boolean rated() {
        return averageRating != null;
    }

    /** "4,8". */
    public String ratingLabel() {
        return rated() ? new DecimalFormat("0.0", ES_UY).format(averageRating) : "";
    }

    /** "(32 opiniones)" o "Nueva" si todavía no tiene valoraciones. */
    public String reviewsLabel() {
        if (reviewCount == 0) return "Nueva";
        return "(" + reviewCount + (reviewCount == 1 ? " opinión)" : " opiniones)");
    }
}
