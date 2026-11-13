package io.github.grigoriev.im.validation;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes form fields and collects the problems of a form. */
public final class Fields {

    /** ISO 3166-1 alpha-2 country code. */
    public static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

    /** A plausible e-mail address. The mail server has the last word. */
    public static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** VAT identification number: country code and 2 to 12 letters or digits. */
    public static final Pattern VAT_ID = Pattern.compile("[A-Z]{2}[A-Z0-9]{2,12}");

    /** Maximum length of an e-mail address, as in the database. */
    public static final int MAX_EMAIL = 254;

    private Fields() {
    }

    /**
     * Returns the text without surrounding spaces.
     *
     * @param value the entered text
     * @return the text, {@code null} if it is empty
     */
    public static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Returns the text in capitals, for codes such as a country.
     *
     * @param value the entered text
     * @return the text, {@code null} if it is empty
     */
    public static String upper(String value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    /**
     * Returns the text in capitals without spaces, for an IBAN or a VAT ID.
     *
     * @param value the entered text
     * @return the text, {@code null} if it is empty
     */
    public static String compact(String value) {
        String text = upper(value);
        return text == null ? null : text.replaceAll("\\s+", "");
    }

    /**
     * Requires a value of at most {@code max} characters.
     *
     * @param problems the problems found so far
     * @param value the normalized value
     * @param field the German name of the field
     * @param max the maximum length
     */
    public static void required(List<String> problems, String value, String field, int max) {
        if (value == null) {
            problems.add("Bitte " + field + " angeben.");
        } else {
            optional(problems, value, field, max);
        }
    }

    /**
     * Limits an optional value to {@code max} characters.
     *
     * @param problems the problems found so far
     * @param value the normalized value, may be {@code null}
     * @param field the German name of the field
     * @param max the maximum length
     */
    public static void optional(List<String> problems, String value, String field, int max) {
        if (value != null && value.length() > max) {
            problems.add(field + " hat höchstens " + max + " Zeichen.");
        }
    }

    /**
     * Requires a value that matches the pattern.
     *
     * @param problems the problems found so far
     * @param value the normalized value
     * @param pattern the pattern
     * @param message the problem if the value is missing or does not match
     */
    public static void matches(List<String> problems, String value, Pattern pattern, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            problems.add(message);
        }
    }

    /**
     * Checks an optional VAT ID.
     *
     * @param problems the problems found so far
     * @param vatId the normalized VAT ID, may be {@code null}
     */
    public static void checkVatId(List<String> problems, String vatId) {
        if (vatId != null && !VAT_ID.matcher(vatId).matches()) {
            problems.add("Die USt-IdNr. beginnt mit dem Ländercode, zum Beispiel DE123456789.");
        }
    }

    /**
     * Checks an e-mail address and its length.
     *
     * @param problems the problems found so far
     * @param email the normalized address
     */
    public static void checkEmail(List<String> problems, String email) {
        matches(problems, email, EMAIL, "Bitte eine gültige E-Mail-Adresse angeben.");
        optional(problems, email, "E-Mail", MAX_EMAIL);
    }

    /**
     * Checks a country code.
     *
     * @param problems the problems found so far
     * @param country the normalized code
     */
    public static void checkCountry(List<String> problems, String country) {
        matches(problems, country, COUNTRY, "Das Land ist ein Code aus zwei Buchstaben, zum Beispiel DE.");
    }
}
