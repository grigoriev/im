package io.github.grigoriev.im.invoice;

import java.time.Instant;

/**
 * An original file of an external invoice as the page lists it: name, type, size and checksum, without the bytes. The
 * bytes leave the store only checked, see {@link OriginalStore#find(long, long)}.
 *
 * @param id database ID
 * @param invoiceId the invoice
 * @param filename the name of the upload
 * @param mediaType PDF, XML, XLSX or DOCX
 * @param sha256 the SHA-256 of the bytes, hexadecimal
 * @param size the number of bytes
 * @param createdAt time of the upload
 * @param createdBy the user who uploaded it
 */
public record OriginalSummary(long id, long invoiceId, String filename, String mediaType, String sha256, int size,
        Instant createdAt, String createdBy) {
}
