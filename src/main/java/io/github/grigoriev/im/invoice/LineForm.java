package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.TaxCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** A line of a draft or template form, with quantity and price as typed. It reads and checks itself. */
public class LineForm {

    static final int MAX_LINE_TEXT = 500;
    static final int MAX_DECIMALS = 4;
    /** Digits before the comma, as {@code NUMERIC(16, 4)} in the database allows. */
    static final int MAX_INTEGER_DIGITS = 12;
    private static final String AT_MOST = " hat höchstens ";

    private String text = "";
    private boolean lumpSum = true;
    private String quantity = "1";
    private String price = "";
    private String amount = "";
    private boolean shownAsLumpSum = true;
    private String shownAmount = "";
    private TaxCategory category;

    /**
     * Returns the service text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the service text.
     *
     * @param text the text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Tells whether the line is a lump sum.
     *
     * @return {@code true} for a lump sum
     */
    public boolean isLumpSum() {
        return lumpSum;
    }

    /**
     * Sets whether the line is a lump sum.
     *
     * @param lumpSum {@code true} for a lump sum
     */
    public void setLumpSum(boolean lumpSum) {
        this.lumpSum = lumpSum;
    }

    /**
     * Returns the quantity as typed.
     *
     * @return the quantity
     */
    public String getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity as typed.
     *
     * @param quantity the quantity
     */
    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    /**
     * Returns the unit price of a line with quantity, as typed.
     *
     * @return the price
     */
    public String getPrice() {
        return price;
    }

    /**
     * Sets the price as typed.
     *
     * @param price the price
     */
    public void setPrice(String price) {
        this.price = price;
    }

    /**
     * Returns the amount of a lump sum as typed. A line with quantity shows its total here, so a switch to a
     * lump sum keeps the amount.
     *
     * @return the amount
     */
    public String getAmount() {
        return amount;
    }

    /**
     * Sets the amount of a lump sum as typed.
     *
     * @param amount the amount
     */
    public void setAmount(String amount) {
        this.amount = amount;
    }

    /**
     * Tells whether the line was a lump sum when the page showed it.
     *
     * @return {@code true} if it was a lump sum
     */
    public boolean isShownAsLumpSum() {
        return shownAsLumpSum;
    }

    /**
     * Sets whether the line was a lump sum when the page showed it.
     *
     * @param shownAsLumpSum {@code true} if it was a lump sum
     */
    public void setShownAsLumpSum(boolean shownAsLumpSum) {
        this.shownAsLumpSum = shownAsLumpSum;
    }

    /**
     * Returns the amount field as the page showed it.
     *
     * @return the amount as shown
     */
    public String getShownAmount() {
        return shownAmount;
    }

    /**
     * Sets the amount field as the page showed it.
     *
     * @param shownAmount the amount as shown
     */
    public void setShownAmount(String shownAmount) {
        this.shownAmount = shownAmount;
    }

    /**
     * Returns the tax category.
     *
     * @return the category
     */
    public TaxCategory getCategory() {
        return category;
    }

    /**
     * Sets the tax category.
     *
     * @param category the category
     */
    public void setCategory(TaxCategory category) {
        this.category = category;
    }

    /**
     * Returns the line as stored. A draft may be incomplete; only unreadable or too long values are problems.
     *
     * @param number the position, from 1, for the messages
     * @param problems the problems found so far; this line adds its own
     * @return the line; not to be stored if a problem was added
     */
    public DraftLine toLine(int number, List<String> problems) {
        String prefix = position(number);
        String content = text == null ? "" : text.strip();
        length(problems, content, prefix + "Die Leistung", MAX_LINE_TEXT);
        if (category == null) {
            problems.add(prefix + "Bitte die Steuer wählen.");
        }
        if (lumpSum && !switchedUntouched()) {
            return new DraftLine(content, true, BigDecimal.ONE, number(problems, amount, prefix + "Der Betrag"), category);
        }
        BigDecimal count = number(problems, quantity, prefix + "Die Menge");
        BigDecimal unitPrice = number(problems, price, prefix + "Der Preis");
        if (!lumpSum) {
            return new DraftLine(content, false, count, unitPrice, category);
        }
        // Switched to a lump sum without a new amount: the total of the quantity and price as submitted now.
        BigDecimal total = count == null || unitPrice == null ? null
                : count.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        if (total != null && total.precision() - total.scale() > MAX_INTEGER_DIGITS) {
            problems.add(prefix + "Der Betrag" + AT_MOST + MAX_INTEGER_DIGITS + " Stellen vor dem Komma.");
            total = null;
        }
        return new DraftLine(content, true, BigDecimal.ONE, total, category);
    }

    /**
     * Tells whether the line turned from "Mit Menge" into a lump sum and its amount field still shows the old total:
     * then the quantity and price as submitted give the amount, so a changed quantity counts (docs/PLAN.md,
     * section 7). An amount the user typed wins.
     *
     * @return {@code true} for a switch with an untouched amount
     */
    boolean switchedUntouched() {
        return lumpSum && !shownAsLumpSum && Objects.equals(blankToNull(amount), blankToNull(shownAmount));
    }

    /**
     * Returns the start of a message about a line.
     *
     * @param number the position, from 1
     * @return for example "Position 2: "
     */
    static String position(int number) {
        return "Position " + number + ": ";
    }

    /**
     * Limits a text to {@code max} characters.
     *
     * @param problems the problems found so far
     * @param value the text, may be {@code null}
     * @param field the German name of the field
     * @param max the maximum length
     */
    static void length(List<String> problems, String value, String field, int max) {
        if (value != null && value.strip().length() > max) {
            problems.add(field + AT_MOST + max + " Zeichen.");
        }
    }

    /**
     * Reads a number. An empty field is 0: a draft may be incomplete, and the check before the issue names the
     * missing amount. On a problem it returns {@code null}: the save then stops before it uses the line.
     */
    private static BigDecimal number(List<String> problems, String text, String field) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        if (DraftForm.ambiguous(text)) {
            problems.add(field + " ist mehrdeutig: bitte mit Komma schreiben, zum Beispiel 1.500,00 oder 1,5.");
            return null;
        }
        BigDecimal value = DraftForm.parse(text);
        if (value == null) {
            problems.add(field + " ist keine Zahl, zum Beispiel 45,00.");
        } else if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            problems.add(field + AT_MOST + MAX_INTEGER_DIGITS + " Stellen vor dem Komma.");
            value = null;
        } else if (value.stripTrailingZeros().scale() > MAX_DECIMALS) {
            problems.add(field + AT_MOST + MAX_DECIMALS + " Nachkommastellen.");
            value = null;
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
