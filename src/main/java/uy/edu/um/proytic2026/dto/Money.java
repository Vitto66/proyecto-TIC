package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Montos en pesos uruguayos como se muestran en pantalla. */
public final class Money {

    private static final DecimalFormatSymbols ES_UY = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-UY"));

    private Money() {
    }

    /** "$ 1.500" o, si tiene centavos, "$ 1.500,50". */
    public static String format(BigDecimal amount) {
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        return "$ " + new DecimalFormat(whole ? "#,##0" : "#,##0.00", ES_UY).format(amount);
    }
}
