package io.github.grigoriev.im.numbering;

import io.github.grigoriev.im.seller.SettingsException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
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

    private static final Pattern TOKEN = Pattern.compile("\\{([^}]*)}");
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
        List<String> problems = new ArrayList<>();
        if (value.length() > MAX_LENGTH) {
            problems.add("Das Muster hat höchstens " + MAX_LENGTH + " Zeichen.");
        }
        boolean year = false;
        int counters = 0;
        Matcher token = TOKEN.matcher(value);
        StringBuilder fixed = new StringBuilder();
        int end = 0;
        while (token.find()) {
            fixed.append(value, end, token.start());
            end = token.end();
            String name = token.group(1);
            if (name.equals("YYYY") || name.equals("YY")) {
                year = true;
            } else if (COUNTER.matcher(name).matches()) {
                counters++;
            } else {
                problems.add("Unbekannter Platzhalter {" + name + "}.");
            }
        }
        fixed.append(value.substring(end));
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
     * Returns the number of an invoice.
     *
     * @param year the year of the issue date
     * @param counter the counter in that year, from 1
     * @return the invoice number
     */
    public String format(int year, int counter) {
        Matcher token = TOKEN.matcher(value);
        StringBuilder number = new StringBuilder();
        while (token.find()) {
            String name = token.group(1);
            String replacement = switch (name) {
                case "YYYY" -> String.format("%04d", year);
                case "YY" -> String.format("%02d", year % 100);
                default -> String.format("%0" + name.length() + "d", counter);
            };
            token.appendReplacement(number, replacement);
        }
        token.appendTail(number);
        return number.toString();
    }
}
