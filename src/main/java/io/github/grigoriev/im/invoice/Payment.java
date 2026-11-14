package io.github.grigoriev.im.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One payment of an issued invoice. Written once, the database refuses a change.
 *
 * @param id database ID, {@code null} before the save
 * @param invoiceId the invoice
 * @param paidOn the day the money came in or went back; it decides the year of the income
 * @param amount the amount in euros, negative for a refund
 * @param note a note, {@code null} if not given
 * @param username who recorded it
 * @param createdAt when it was recorded
 */
@Table("payment")
public record Payment(@Id Long id, long invoiceId, LocalDate paidOn, BigDecimal amount, String note, String username,
        Instant createdAt) {
}
