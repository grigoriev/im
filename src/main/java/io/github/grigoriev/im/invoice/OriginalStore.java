package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.integrity.Seal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Keeps the original files of external invoices (docs/PLAN.md, section 7, "External invoices"). A file is written once
 * with its SHA-256 and seal, and leaves the store only if it still matches both.
 */
@Component
public class OriginalStore {

    static final String PDF = "application/pdf";
    static final String XML = "application/xml";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String WRONG_TYPE = "Bitte eine PDF-, XLSX-, DOCX- oder XML-Datei wählen.";
    static final String NO_FILE = "Bitte eine Datei wählen.";
    static final String DAMAGED = "Die Datei %s ist beschädigt oder wurde außerhalb der App geändert. Die Seite der "
            + "Rechnung zeigt, was abweicht.";
    static final String WRONG_SHA = "%s: Der Inhalt passt nicht zur gespeicherten Prüfsumme. Gespeichert: %s, "
            + "berechnet: %s.";
    static final String WRONG_SEAL = "%s: Das Siegel passt nicht. Prüfsumme oder Zuordnung der Datei wurden außerhalb "
            + "der App geändert.";

    /** The ID comes first: the seal names it. */
    static final String NEXT_ID = "SELECT nextval(pg_get_serial_sequence('invoice_original', 'id'))";
    static final String INSERT = """
            INSERT INTO invoice_original (id, invoice_id, filename, media_type, content, sha256, size, seal, created_at,
                created_by)
            OVERRIDING SYSTEM VALUE
            VALUES (:id, :invoiceId, :filename, :mediaType, :content, :sha256, :size, :seal, :createdAt, :createdBy)""";
    static final String COLUMNS = "id, invoice_id, filename, media_type, content, sha256, size, seal, created_at, "
            + "created_by";
    static final String OF_INVOICE = "SELECT " + COLUMNS + " FROM invoice_original WHERE invoice_id = :invoiceId "
            + "ORDER BY id";
    static final String ONE = "SELECT " + COLUMNS + " FROM invoice_original WHERE invoice_id = :invoiceId AND id = :id";

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    /** The signature of an entry in the central directory of a ZIP. */
    private static final byte[] CENTRAL = {'P', 'K', 1, 2};
    private static final int NAME_LENGTH = 28;
    private static final int NAME = 46;
    /** The main part of each Office type, named in the central directory. */
    private static final Map<String, String> OFFICE = Map.of("xl/workbook.xml", XLSX, "word/document.xml", DOCX);
    private static final Map<String, String> EXTENSIONS = Map.of(PDF, ".pdf", XML, ".xml", XLSX, ".xlsx", DOCX,
            ".docx");
    private static final String INVOICE_ID = "invoiceId";

    private final JdbcClient jdbc;
    private final Seal seal;

    /**
     * Creates the store.
     *
     * @param jdbc reads and writes the files
     * @param seal seals the files
     */
    public OriginalStore(JdbcClient jdbc, Seal seal) {
        this.jdbc = jdbc;
        this.seal = seal;
    }

    /**
     * Returns the type of an original file from its bytes, and adds a problem for anything else.
     *
     * @param problems receives what is wrong
     * @param content the bytes
     * @return PDF, XML, XLSX or DOCX; empty for no file, a file over 25 MB or another type
     */
    public static Optional<String> mediaType(List<String> problems, byte[] content) {
        if (content == null || content.length == 0) {
            problems.add(NO_FILE);
            return Optional.empty();
        }
        if (content.length > DocumentService.MAX_BYTES) {
            problems.add(DocumentService.TOO_LARGE);
            return Optional.empty();
        }
        Optional<String> type = type(content);
        if (type.isEmpty()) {
            problems.add(WRONG_TYPE);
        }
        return type;
    }

    private static Optional<String> type(byte[] content) {
        if (startsWith(content, 0, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        if (startsWith(content, 0, ZIP_MAGIC)) {
            return office(content);
        }
        return startsWithTag(content) ? Optional.of(XML) : Optional.empty();
    }

    /**
     * Reads the names of the central directory, without unpacking a byte: an Office file names its main part there.
     */
    static Optional<String> office(byte[] content) {
        for (int at = 0; at <= content.length - NAME; at++) {
            if (startsWith(content, at, CENTRAL)) {
                int length = (content[at + NAME_LENGTH] & 0xFF) | (content[at + NAME_LENGTH + 1] & 0xFF) << 8;
                if (at + NAME + length <= content.length) {
                    String type = OFFICE.get(new String(content, at + NAME, length, StandardCharsets.UTF_8));
                    if (type != null) {
                        return Optional.of(type);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** An XML file starts with a tag, after a byte order mark and white space. */
    static boolean startsWithTag(byte[] content) {
        int at = startsWith(content, 0, BOM) ? BOM.length : 0;
        while (at < content.length && Character.isWhitespace(content[at])) {
            at++;
        }
        return at + 1 < content.length && content[at] == '<'
                && (content[at + 1] == '?' || Character.isLetter(content[at + 1]));
    }

    private static boolean startsWith(byte[] content, int at, byte[] magic) {
        return content.length - at >= magic.length
                && Arrays.equals(content, at, at + magic.length, magic, 0, magic.length);
    }

    /**
     * Saves an original file. The caller checked its type with {@link #mediaType}.
     *
     * @param invoiceId the invoice
     * @param number the invoice number, for the name of a file uploaded without one
     * @param uploaded the name of the upload, may be {@code null}
     * @param content the bytes
     * @param mediaType the type of the file
     * @param at time of the upload
     * @return the saved file
     */
    public InvoiceOriginal store(long invoiceId, String number, String uploaded, byte[] content, String mediaType,
            Instant at) {
        long id = jdbc.sql(NEXT_ID).query(Long.class).single();
        String sha256 = Seal.sha256(content);
        String filename = DocumentService.safeName(uploaded,
                "Rechnung_" + number.replace('/', '-') + EXTENSIONS.get(mediaType));
        String user = AuditService.currentUser();
        String sealed = seal.invoiceOriginal(invoiceId, id, sha256);
        jdbc.sql(INSERT).param("id", id).param(INVOICE_ID, invoiceId).param("filename", filename)
                .param("mediaType", mediaType).param("content", content).param("sha256", sha256)
                .param("size", content.length).param("seal", sealed).param("createdAt", Timestamp.from(at))
                .param("createdBy", user).update();
        return new InvoiceOriginal(id, invoiceId, filename, mediaType, content, sha256, content.length, sealed, at,
                user);
    }

    /**
     * Lists the original files of an invoice.
     *
     * @param invoiceId the invoice
     * @return the files in the order of their upload; empty for an invoice of the app
     */
    public List<InvoiceOriginal> list(long invoiceId) {
        return jdbc.sql(OF_INVOICE).param(INVOICE_ID, invoiceId).query(OriginalStore::row).list();
    }

    /**
     * Returns one original file of an invoice, after it passed the check.
     *
     * @param invoiceId the invoice
     * @param id the file
     * @return the file, empty if the invoice has no such file
     * @throws InvoiceException if the file does not match its SHA-256 or seal
     */
    public Optional<InvoiceOriginal> find(long invoiceId, long id) {
        Optional<InvoiceOriginal> original = jdbc.sql(ONE).param(INVOICE_ID, invoiceId).param("id", id)
                .query(OriginalStore::row).optional();
        original.ifPresent(this::verified);
        return original;
    }

    /**
     * Checks an original file.
     *
     * @param original the file
     * @return the file
     * @throws InvoiceException if it does not match its SHA-256 or seal
     */
    public InvoiceOriginal verified(InvoiceOriginal original) {
        if (!problems(original).isEmpty()) {
            throw new InvoiceException(List.of(DAMAGED.formatted(original.filename())));
        }
        return original;
    }

    /**
     * Checks an original file against its SHA-256 and seal.
     *
     * @param original the file
     * @return what is wrong, empty if it is in order
     */
    public List<String> problems(InvoiceOriginal original) {
        List<String> problems = new ArrayList<>();
        String computed = Seal.sha256(original.content());
        if (!computed.equals(original.sha256())) {
            problems.add(WRONG_SHA.formatted(original.filename(), original.sha256(), computed));
        }
        if (!Seal.matches(original.seal(), seal.invoiceOriginal(original.invoiceId(), original.id(),
                original.sha256()))) {
            problems.add(WRONG_SEAL.formatted(original.filename()));
        }
        return problems;
    }

    private static InvoiceOriginal row(ResultSet rs, int n) throws SQLException {
        return new InvoiceOriginal(rs.getLong("id"), rs.getLong("invoice_id"), rs.getString("filename"),
                rs.getString("media_type"), rs.getBytes("content"), rs.getString("sha256"), rs.getInt("size"),
                rs.getString("seal"), rs.getTimestamp("created_at").toInstant(), rs.getString("created_by"));
    }
}
