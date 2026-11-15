package io.github.grigoriev.im.document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A business document without its bytes, as the pages list it.
 *
 * @param id database ID
 * @param category the kind
 * @param title the title, for example "Einkommensteuerbescheid 2027"
 * @param documentDate the date on the document
 * @param year the year it concerns, {@code null} for none
 * @param note a note, empty for none
 * @param replacesId the version it replaces, {@code null} for the first one
 * @param replacedBy the version that replaces it, {@code null} for the current one
 * @param filename the file name
 * @param mediaType PDF, JPEG or PNG
 * @param sha256 the SHA-256 of the file, hexadecimal
 * @param size the number of bytes
 * @param createdAt when it was stored
 * @param createdBy who stored it
 */
public record BusinessDocument(long id, DocumentCategory category, String title, LocalDate documentDate, Integer year,
        String note, Long replacesId, Long replacedBy, String filename, String mediaType, String sha256, int size,
        Instant createdAt, String createdBy) {

    /**
     * Tells whether no newer version replaces it.
     *
     * @return {@code true} for the current version
     */
    public boolean current() {
        return replacedBy == null;
    }

    /**
     * Returns the year the document belongs to in the archive: the year it concerns, else the year of its date.
     *
     * @return the year
     */
    public int archiveYear() {
        return year != null ? year : documentDate.getYear();
    }
}
