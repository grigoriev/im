package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An invoice. A draft takes the customer's current data; the issue (M2d) freezes it.
 *
 * @param id database ID, {@code null} before the first save
 * @param status where the invoice is in its life
 * @param customerId the customer
 * @param number the invoice number, {@code null} until the issue
 * @param format the e-invoice format
 * @param issueDate the invoice date
 * @param serviceFrom first day of the service
 * @param serviceTo last day of the service
 * @param paymentDays days until payment is due
 * @param buyerReference buyer reference, for example the Leitweg-ID; may be {@code null}
 * @param intro text above the lines, may be {@code null}
 * @param closing text below the lines, may be {@code null}
 * @param taxNote the tax note, may be {@code null}
 * @param lines the lines, in order
 * @param createdAt time of creation
 * @param updatedAt time of the last change
 * @param version counts the saves; a save with an older version is refused, so two windows never overwrite
 *     each other
 * @param issuedAt time of the issue, {@code null} for a draft
 * @param snapshot the issued invoice document as JSON, frozen at the issue; {@code null} for a draft
 */
@Table("invoice")
public record Invoice(
        @Id Long id,
        InvoiceStatus status,
        Long customerId,
        String number,
        InvoiceFormat format,
        LocalDate issueDate,
        LocalDate serviceFrom,
        LocalDate serviceTo,
        int paymentDays,
        String buyerReference,
        String intro,
        String closing,
        String taxNote,
        @MappedCollection(idColumn = "invoice_id", keyColumn = "position") List<DraftLine> lines,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version,
        Instant issuedAt,
        String snapshot) {

    /** Copies the lines, so a stored invoice never shares a list. */
    public Invoice {
        lines = List.copyOf(lines);
    }

    /**
     * Returns the due date.
     *
     * @return the invoice date plus the payment days
     */
    public LocalDate dueDate() {
        return issueDate.plusDays(paymentDays);
    }

    /**
     * Tells whether the invoice can still change.
     *
     * @return {@code true} for a draft
     */
    public boolean isDraft() {
        return status == InvoiceStatus.DRAFT;
    }
}
