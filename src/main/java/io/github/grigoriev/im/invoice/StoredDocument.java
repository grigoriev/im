package io.github.grigoriev.im.invoice;

import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A document of an issued invoice. It never changes: the database refuses an update or a delete.
 *
 * @param id database ID, {@code null} before the first save
 * @param invoiceId the invoice
 * @param kind PDF or XML
 * @param filename the file name, for example {@code Rechnung_2027-01.pdf}
 * @param content the bytes
 * @param sha256 the SHA-256 of the bytes, hexadecimal
 * @param size the number of bytes
 * @param createdAt time of the issue
 */
@Table("invoice_document")
public record StoredDocument(@Id Long id, Long invoiceId, DocumentKind kind, String filename, byte[] content,
        String sha256, int size, Instant createdAt) {

    /** Keeps a copy of the bytes, so the document never changes from outside. */
    public StoredDocument {
        content = content.clone();
    }

    /**
     * Returns a copy of the bytes.
     *
     * @return the bytes
     */
    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StoredDocument that && Objects.equals(id, that.id)
                && Objects.equals(invoiceId, that.invoiceId) && kind == that.kind
                && Objects.equals(sha256, that.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, invoiceId, kind, sha256);
    }

    @Override
    public String toString() {
        return "StoredDocument[" + filename + ", " + size + " bytes, sha256=" + sha256 + "]";
    }
}
