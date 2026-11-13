package io.github.grigoriev.im.numbering;

import io.github.grigoriev.im.seller.SettingsException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The pattern of the invoice numbers, for example {@code {YYYY}-{NN}} for {@code 2027-01}.
 *
 * <p>Tokens: {@code {YYYY}} and {@code {YY}} for the year, {@code {N}} to {@code {NNNNNN}} for the counter with
 * that many digits at least. Everything else is fixed text: letters, digits and {@code - _ . /}. The counter
 * restarts each year, so a pattern needs a year and a counter, or numbers would repeat.
 *
 * @param value the pattern
 */
public record NumberPattern(String value) {

    /** The pattern before the admin sets one. */
    public static final NumberPattern DEFAULT = new NumberPattern("{YYYY}-{NN}");

    /** Maximum length of a pattern. */
    static final int MAX_LENGTH = 32;

    private static final Pattern COUNTER = Pattern.compile("N{1,6}");
    private static final Pattern TEXT = Pattern.compile("[A-Za-z0-9._/-]*");

    /**
     * Checks a pattern as the admin entered it.
     *
     * @param text the pattern
     * @return the pattern without surrounding spaces
     * @throws SettingsException with every problem found
     */
    public static NumberPattern parse(String text) {
        String value = text == null ? "" : text.strip();
        if (value.length() > MAX_LENGTH) {
            throw new SettingsException(List.of("Das Muster hat höchstens " + MAX_LENGTH + " Zeichen."));
        }
        List<String> problems = new ArrayList<>();
        boolean year = false;
        int counters = 0;
        StringBuilder fixed = new StringBuilder();
        for (Part part : parts(value)) {
            if (!part.token()) {
                fixed.append(part.text());
            } else if (part.text().equals("YYYY") || part.text().equals("YY")) {
                year = true;
            } else if (COUNTER.matcher(part.text()).matches()) {
                counters++;
            } else {
                problems.add("Unbekannter Platzhalter {" + part.text() + "}.");
            }
        }
        if (!TEXT.matcher(fixed).matches()) {
            problems.add("Fester Text besteht aus Buchstaben, Ziffern und - _ . /");
        }
        if (!year) {
            problems.add("Das Muster braucht das Jahr, {YYYY} oder {YY}: der Zähler beginnt jedes Jahr neu.");
        }
        if (counters != 1) {
            problems.add("Das Muster braucht genau einen Zähler, zum Beispiel {NN}.");
        }
        if (!problems.isEmpty()) {
            throw new SettingsException(problems);
        }
        return new NumberPattern(value);
    }

    /**
     * Returns the number of an invoice. The digits are always ASCII, whatever the locale of the server.
     *
     * @param year the year of the issue date
     * @param counter the counter in that year, from 1
     * @return the invoice number
     */
    public String format(int year, int counter) {
        StringBuilder number = new StringBuilder();
        for (Part part : parts(value)) {
            if (!part.token()) {
                number.append(part.text());
            } else if (part.text().equals("YYYY")) {
                number.append(padded(year, 4));
            } else if (part.text().equals("YY")) {
                number.append(padded(year % 100, 2));
            } else {
                number.append(padded(counter, part.text().length()));
            }
        }
        return number.toString();
    }

    /** A piece of a pattern: fixed text, or the name of a token without its braces. */
    private record Part(String text, boolean token) {
    }

    /** Splits a pattern into fixed text and tokens in one pass, without a regular expression. */
    private static List<Part> parts(String value) {
        List<Part> parts = new ArrayList<>();
        int position = 0;
        while (position != value.length()) {
            int open = value.indexOf('{', position);
            int close = open == -1 ? -1 : value.indexOf('}', open);
            if (close == -1) {
                parts.add(new Part(value.substring(position), false));
                break;
            }
            parts.add(new Part(value.substring(position, open), false));
            parts.add(new Part(value.substring(open + 1, close), true));
            position = close + 1;
        }
        return parts;
    }

    private static String padded(int value, int digits) {
        String text = Integer.toString(value);
        return "0".repeat(Math.max(0, digits - text.length())) + text;
    }
}
