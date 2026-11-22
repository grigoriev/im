package io.github.grigoriev.im.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** German number, money and date formats for what the user reads: the PDF, the e-mails and their placeholders. */
public final class GermanFormat {

    /** The decimals of a unit price, as many as a line of an invoice allows. */
    static final int PRICE_DECIMALS = 4;

    private static final Locale GERMAN = Locale.GERMANY;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", GERMAN);

    /**
     * Formats an amount, for example {@code 1.234,50 €}.
     *
     * @param amount the amount
     * @return the formatted amount
     */
    public String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(GERMAN);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }

    /**
     * Formats a unit price with two to four decimals, for example {@code 0,005 €}.
     *
     * @param price the unit price
     * @return the formatted price
     */
    public String price(BigDecimal price) {
        NumberFormat format = NumberFormat.getCurrencyInstance(GERMAN);
        format.setMaximumFractionDigits(PRICE_DECIMALS);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(price);
    }

    /**
     * Formats a VAT rate, for example {@code 19 %} or {@code 7 %}.
     *
     * @param rate the rate in percent
     * @return the formatted rate
     */
    public String percent(BigDecimal rate) {
        return quantity(rate) + " %";
    }

    /**
     * Formats a quantity without trailing zeros, for example {@code 1,5}.
     *
     * @param quantity the quantity
     * @return the formatted quantity
     */
    public String quantity(BigDecimal quantity) {
        NumberFormat format = NumberFormat.getNumberInstance(GERMAN);
        format.setMaximumFractionDigits(4);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(quantity);
    }

    /**
     * Formats a date, for example {@code 18.03.2027}.
     *
     * @param date the date
     * @return the formatted date
     */
    public String date(LocalDate date) {
        return DATE.format(date);
    }

    /**
     * Groups an IBAN in blocks of four, for example {@code DE02 1203 0000 ...}.
     *
     * @param iban the IBAN without spaces
     * @return the grouped IBAN
     */
    public String iban(String iban) {
        return iban.replaceAll("(.{4})(?!$)", "$1 ");
    }

    /**
     * Formats a service period. A period of one day shows that day only.
     *
     * @param from first day
     * @param to last day
     * @return the formatted period
     */
    public String period(LocalDate from, LocalDate to) {
        return from.equals(to) ? date(from) : date(from) + " bis " + date(to);
    }

    /**
     * Signs a text: the name on the line after its last line. A name typed as the last line moves up to the line after
     * the greeting, without blank lines between; a last line that ends with the name, such as "Gruß, Elena Sommer",
     * signs already.
     *
     * @param text the text, for example the closing
     * @param signer the name
     * @return the signed text
     */
    public String signed(String text, String signer) {
        String body = text.strip();
        int last = body.lastIndexOf('\n') + 1;
        String lastLine = body.substring(last).strip();
        if (lastLine.equals(signer)) {
            body = body.substring(0, last).strip();
        } else if (lastLine.endsWith(signer)
                && !Character.isLetterOrDigit(lastLine.charAt(lastLine.length() - signer.length() - 1))) {
            // The name as a word of its own, not the end of a longer one: "ASAP" is not signed by "SAP".
            return body;
        }
        return body.isEmpty() ? signer : body + "\n" + signer;
    }

    /**
     * Names a country for the last line of an address, in German capitals as DIN 5008 asks.
     *
     * @param code the ISO country code
     * @return for example {@code ÖSTERREICH}
     */
    public String country(String code) {
        return Locale.of("", code).getDisplayCountry(GERMAN).toUpperCase(GERMAN);
    }
}
