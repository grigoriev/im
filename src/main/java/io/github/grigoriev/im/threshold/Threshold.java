package io.github.grigoriev.im.threshold;

import java.math.BigDecimal;

/**
 * The §19 revenue of a year and the year before, by payment received.
 *
 * @param year the year
 * @param previous the counted revenue of the year before, from the baseline and its payments
 * @param previousFromBaseline whether the year before has a baseline
 * @param current the counted revenue of the year so far
 * @param excluded the revenue of the year that does not count, for example §4 Nr. 21 or abroad
 * @param received all money received in the year, refunds subtracted
 * @param forecast the counted revenue at the current rate until the end of the year
 */
public record Threshold(int year, BigDecimal previous, boolean previousFromBaseline, BigDecimal current,
        BigDecimal excluded, BigDecimal received, BigDecimal forecast) {

    /** The limit of the year before (§19 Abs. 1 UStG). */
    public static final BigDecimal PREVIOUS_LIMIT = new BigDecimal("25000");

    /** The limit of the current year; above it regular VAT applies from that sale on. */
    public static final BigDecimal CURRENT_LIMIT = new BigDecimal("100000");

    /**
     * Tells whether the Kleinunternehmer rule applies this year.
     *
     * @return {@code true} while the year before stayed within 25 000 and this year within 100 000 euros
     */
    public boolean applies() {
        return previous.compareTo(PREVIOUS_LIMIT) <= 0 && current.compareTo(CURRENT_LIMIT) <= 0;
    }

    /**
     * Tells whether the forecast puts next year's rule at risk.
     *
     * @return {@code true} if the forecast is above 25 000 euros
     */
    public boolean nextYearAtRisk() {
        return forecast.compareTo(PREVIOUS_LIMIT) > 0;
    }

    /**
     * Returns a share of a limit for a meter.
     *
     * @param amount the amount
     * @param limit the limit
     * @return the percent, from 0 to 100
     */
    public static int percent(BigDecimal amount, BigDecimal limit) {
        return amount.multiply(BigDecimal.valueOf(100)).divide(limit, 0, java.math.RoundingMode.DOWN)
                .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).intValueExact();
    }
}
