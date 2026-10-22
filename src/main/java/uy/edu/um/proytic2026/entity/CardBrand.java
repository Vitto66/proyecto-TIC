package uy.edu.um.proytic2026.entity;

/** Marca de una tarjeta, deducida de sus primeros dígitos. */
public enum CardBrand {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("American Express"),
    OTHER("Tarjeta");

    private final String label;

    CardBrand(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param digits número de tarjeta, solo dígitos. */
    public static CardBrand detect(String digits) {
        if (digits == null || digits.length() < 2) {
            return OTHER;
        }
        if (digits.startsWith("4")) {
            return VISA;
        }
        if (digits.startsWith("34") || digits.startsWith("37")) {
            return AMEX;
        }
        int two = Integer.parseInt(digits.substring(0, 2));
        if (two >= 51 && two <= 55) {
            return MASTERCARD;
        }
        if (digits.length() >= 4) {
            int four = Integer.parseInt(digits.substring(0, 4));
            if (four >= 2221 && four <= 2720) {
                return MASTERCARD;
            }
        }
        return OTHER;
    }
}
