package uy.edu.um.proytic2026.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fechas y horas como se muestran en pantalla, en español de Uruguay. */
public final class DateLabels {

    private static final Locale ES_UY = Locale.forLanguageTag("es-UY");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES_UY);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("EEE d MMM", ES_UY);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES_UY);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", ES_UY);

    private DateLabels() {
    }

    /** "Sábado 25 de octubre de 2026". */
    public static String day(LocalDate date) {
        return capitalize(DAY.format(date));
    }

    /** "Sáb 25 oct" (sin puntos de abreviatura). */
    public static String shortDay(LocalDate date) {
        return capitalize(SHORT_DAY.format(date).replace(".", ""));
    }

    /** "Noviembre de 2026". */
    public static String month(YearMonth month) {
        return capitalize(MONTH.format(month));
    }

    /** "10:30". */
    public static String time(LocalDateTime dateTime) {
        return TIME.format(dateTime);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
