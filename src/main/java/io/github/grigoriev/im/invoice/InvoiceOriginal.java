package io.github.grigoriev.im.invoice;

import java.time.Instant;
import java.util.Objects;

/**
 * An original file of an external invoice. It never changes: the database refuses an update or a delete.
 *
 * @param id database ID
 * @param invoiceId the invoice
 * @param filename the name of the upload
 * @param mediaType PDF, XML, XLSX or DOCX
 * @param content the bytes
 * @param sha256 the SHA-256 of the bytes, hexadecimal
 * @param size the number of bytes
 * @param seal the HMAC of invoice, ID and SHA-256 with the key of the app
 * @param createdAt time of the upload
 * @param createdBy the user who uploaded it
 */
public record InvoiceOriginal(long id, long invoiceId, String filename, String mediaType, byte[] content,
        String sha256, int size, String seal, Instant createdAt, String createdBy) {

    /** Keeps a copy of the bytes, so the file never changes from outside. */
    public InvoiceOriginal {
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
        return other instanceof InvoiceOriginal that && id == that.id && invoiceId == that.invoiceId
                && Objects.equals(sha256, that.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, invoiceId, sha256);
    }

    @Override
    public String toString() {
        return "InvoiceOriginal[" + filename + ", " + size + " bytes, sha256=" + sha256 + "]";
    }
}
