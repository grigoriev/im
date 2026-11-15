package io.github.grigoriev.im.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An expense without the bytes of its receipt, as the pages list it.
 *
 * @param id database ID
 * @param paidOn the day of the payment: the expense counts in its year
 * @param amount the amount paid, with VAT; a Kleinunternehmerin deducts no input tax
 * @param category the kind
 * @param vendor who was paid
 * @param description what was bought
 * @param asset whether it is an asset written off over years (Anlagegut); WISO computes the AfA
 * @param cancelledAt when it was cancelled, {@code null} for a valid expense
 * @param cancelReason why it was cancelled, {@code null} for a valid expense
 * @param filename the file name of the receipt
 * @param mediaType PDF, JPEG or PNG
 * @param sha256 the SHA-256 of the receipt, hexadecimal
 * @param size the number of bytes of the receipt
 * @param createdAt when it was entered
 * @param createdBy who entered it
 */
public record Expense(long id, LocalDate paidOn, BigDecimal amount, ExpenseCategory category, String vendor,
        String description, boolean asset, Instant cancelledAt, String cancelReason, String filename, String mediaType,
        String sha256, int size, Instant createdAt, String createdBy) {

    /**
     * Tells whether it was cancelled. A cancelled expense stays, but counts no more.
     *
     * @return {@code true} if cancelled
     */
    public boolean cancelled() {
        return cancelledAt != null;
    }
}
