package io.github.grigoriev.im.validation;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Reads amounts as Germans type them, for every form with money: {@code 1.234,56} or {@code 45,5}. */
public final class Amounts {

    /** German digits with thousands dots and an optional decimal comma: {@code 1.234,56}. */
    private static final Pattern THOUSANDS = Pattern.compile("-?\\d{1,3}(\\.\\d{3})+(,\\d+)?");

    /** Digits with an optional decimal comma: {@code 45,5}. */
    private static final Pattern COMMA = Pattern.compile("-?\\d+(,\\d+)?");

    /** One dot before exactly three digits: {@code 1.500} is 1500 for a German and 1,5 for a keypad. */
    private static final Pattern AMBIGUOUS = Pattern.compile("-?\\d{1,3}\\.\\d{3}");

    /** Digits with a decimal dot, as a Mac keypad sends them. */
    private static final Pattern PLAIN = Pattern.compile("-?\\d+\\.\\d+");

    private Amounts() {
    }

    /**
     * Reads an amount as Germans type it. A dot alone counts as decimal point, as a Mac keypad sends it.
     *
     * @param text the entered text
     * @return the amount, or {@code null} if the text is no number
     */
    public static BigDecimal parse(String text) {
        String value = text == null ? "" : text.replace(" ", "").replace("€", "").strip();
        if (AMBIGUOUS.matcher(value).matches()) {
            return null;
        }
        if (PLAIN.matcher(value).matches()) {
            return new BigDecimal(value);
        }
        if (THOUSANDS.matcher(value).matches() || COMMA.matcher(value).matches()) {
            return new BigDecimal(value.replace(".", "").replace(",", "."));
        }
        return null;
    }

    /**
     * Tells whether a text reads both as thousands and as decimals, such as {@code 1.500}.
     *
     * @param text the entered text
     * @return {@code true} if the text is ambiguous
     */
    public static boolean ambiguous(String text) {
        return text != null && AMBIGUOUS.matcher(text.replace(" ", "").replace("€", "").strip()).matches();
    }
}
