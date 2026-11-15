package io.github.grigoriev.im.invoice;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One payment reminder of an issued invoice. Written once, the database refuses a change.
 *
 * @param id database ID, {@code null} before the save
 * @param invoiceId the invoice
 * @param remindedOn the day of the reminder
 * @param channel how it reached the customer
 * @param recipient to whom, {@code null} if not given
 * @param note a note, {@code null} if not given
 * @param username who recorded it
 * @param createdAt when it was recorded
 */
@Table("reminder")
public record Reminder(@Id Long id, long invoiceId, LocalDate remindedOn, ReminderChannel channel, String recipient,
        String note, String username, Instant createdAt) {
}
