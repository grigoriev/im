package io.github.grigoriev.im.threshold;

import io.github.grigoriev.im.einvoice.TaxCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One payment of a year as the reports count it: rounded to cents per payment, so the rows add up to the totals.
 *
 * @param paidOn the day the money came in or went back
 * @param number the invoice number
 * @param customer the buyer of the invoice's snapshot
 * @param amount the amount, negative for a refund
 * @param counted the part that counts for §19
 * @param excluded the rest: §4 Nr. 21, abroad and other revenue that does not count
 * @param byCategory the amount per tax category of the invoice's lines
 * @param note the note of the payment, {@code null} if none
 */
public record RevenueRow(LocalDate paidOn, String number, String customer, BigDecimal amount, BigDecimal counted,
        BigDecimal excluded, Map<TaxCategory, BigDecimal> byCategory, String note) {

    /** Copies the map. */
    public RevenueRow {
        byCategory = Map.copyOf(byCategory);
    }
}
