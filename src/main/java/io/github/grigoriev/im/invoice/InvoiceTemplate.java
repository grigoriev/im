package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A template of a customer. A new draft copies it; a later change of the template leaves the invoices as they are.
 * Texts may hold placeholders such as {@code {Leistungszeitraum}}.
 *
 * @param id database ID, {@code null} before the first save
 * @param customerId the customer
 * @param name the name, for example "Lesung mit Gespräch"
 * @param format the e-invoice format of new drafts
 * @param paymentDays days until payment is due
 * @param intro text above the lines, may be {@code null}
 * @param closing text below the lines, may be {@code null}
 * @param taxNote the tax note, may be {@code null}
 * @param emailSubject subject of the e-mail with the invoice, may be {@code null}
 * @param emailBody text of the e-mail with the invoice, may be {@code null}
 * @param lines the lines, in order
 * @param createdAt time of creation
 * @param updatedAt time of the last change
 * @param version counts the saves; a save with an older version is refused
 */
@Table("invoice_template")
public record InvoiceTemplate(
        @Id Long id,
        Long customerId,
        String name,
        InvoiceFormat format,
        int paymentDays,
        String intro,
        String closing,
        String taxNote,
        String emailSubject,
        String emailBody,
        @MappedCollection(idColumn = "template_id", keyColumn = "position") List<TemplateLine> lines,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version) {

    /** Copies the lines, so a stored template never shares a list. */
    public InvoiceTemplate {
        lines = List.copyOf(lines);
    }

    /**
     * Returns the texts of the lines, for the list of templates.
     *
     * @return the texts joined by a middle dot
     */
    public String summary() {
        return String.join(" · ", lines.stream().map(TemplateLine::text).filter(text -> !text.isBlank()).toList());
    }
}
