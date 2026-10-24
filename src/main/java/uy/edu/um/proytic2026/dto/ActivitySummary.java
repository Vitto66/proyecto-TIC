package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityType;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Datos de una actividad para mostrar en pantalla. Se arma dentro de la transacción,
 * así las vistas no dependen de relaciones LAZY (open-in-view está apagado).
 */
public record ActivitySummary(Long id, String name, String localityName, String departmentName,
                              int durationMinutes, BigDecimal price, int minAge,
                              List<String> typeNames, boolean active) {

    public static ActivitySummary from(Activity a) {
        return new ActivitySummary(a.getId(), a.getName(),
                a.getLocality().getName(), a.getLocality().getDepartment().getName(),
                a.getDurationMinutes(), a.getPrice(), a.getMinAge(),
                a.getTypes().stream().map(ActivityType::getName).sorted(Comparator.naturalOrder()).toList(),
                a.isActive());
    }

    /** "La Paloma, Rocha", o solo el departamento si se llaman igual. */
    public String location() {
        return localityName.equalsIgnoreCase(departmentName)
                ? departmentName
                : localityName + ", " + departmentName;
    }

    /** "45 min", "2 h", "2 h 30 min". */
    public String durationLabel() {
        return formatMinutes(durationMinutes);
    }

    /** "$ 1.500" o, si tiene centavos, "$ 1.500,50", con el formato uruguayo. */
    public String priceLabel() {
        boolean whole = price.stripTrailingZeros().scale() <= 0;
        DecimalFormat format = new DecimalFormat(whole ? "#,##0" : "#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-UY")));
        return "$ " + format.format(price);
    }

    public String typesLabel() {
        return String.join(" · ", typeNames);
    }

    public static String formatMinutes(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) return rest + " min";
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }
}
