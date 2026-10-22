package uy.edu.um.proytic2026.validation;

import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reglas de formato de una tarjeta. No guardan ni registran el número en ningún lado. */
public final class PaymentCardRules {

    private static final Pattern EXPIRY = Pattern.compile("(0[1-9]|1[0-2])\\s*/\\s*(\\d{2}|\\d{4})");
    private static final Pattern CVV = Pattern.compile("\\d{3,4}");

    private PaymentCardRules() {
    }

    /** El número sin espacios ni guiones, como se suele escribir ("4242 4242 4242 4242"). */
    public static String digits(String cardNumber) {
        return cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
    }

    /** Entre 13 y 19 dígitos y dígito verificador correcto (algoritmo de Luhn). */
    public static boolean isValidNumber(String cardNumber) {
        String d = digits(cardNumber);
        if (!d.matches("\\d{13,19}")) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = d.length() - 1; i >= 0; i--) {
            int n = d.charAt(i) - '0';
            if (doubleIt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** "MM/AA" o "MM/AAAA"; null si el formato no es válido. */
    public static YearMonth parseExpiry(String expiry) {
        if (expiry == null) {
            return null;
        }
        Matcher m = EXPIRY.matcher(expiry.trim());
        if (!m.matches()) {
            return null;
        }
        int month = Integer.parseInt(m.group(1));
        int year = Integer.parseInt(m.group(2));
        if (year < 100) {
            year += 2000;
        }
        return YearMonth.of(year, month);
    }

    public static boolean isValidCvv(String cvv) {
        return cvv != null && CVV.matcher(cvv.trim()).matches();
    }

    public static String last4(String cardNumber) {
        String d = digits(cardNumber);
        return d.substring(d.length() - 4);
    }
}
