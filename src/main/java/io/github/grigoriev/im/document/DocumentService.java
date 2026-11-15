package io.github.grigoriev.im.document;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.IntegrityCheck;
import io.github.grigoriev.im.integrity.Seal;
import io.github.grigoriev.im.validation.Fields;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the business documents (docs/PLAN.md, section 7, "Documents"). A file is written once and never goes away: a
 * new version names the one it replaces. Only the description of a document may change.
 */
@Service
public class DocumentService implements IntegrityCheck {

    /** The largest file, 25 MiB: a scanned letter of many pages fits. */
    /** The largest file of a document; the upload checks it before it reads the file. */
    public static final int MAX_BYTES = 25 * 1024 * 1024;
    /** The problem of a file over {@link #MAX_BYTES}. */
    public static final String TOO_LARGE = "Die Datei ist größer als " + MAX_BYTES / 1024 / 1024 + " MB.";
    static final int MAX_TITLE = 200;
    static final int MAX_NOTE = 2000;
    static final int MAX_FILENAME = 200;
    static final int FIRST_YEAR = 2000;
    static final int LAST_YEAR = 2100;

    static final String PDF = "application/pdf";
    static final String JPEG = "image/jpeg";
    static final String PNG = "image/png";

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Map<String, String> EXTENSIONS = Map.of(PDF, ".pdf", JPEG, ".jpg", PNG, ".png");
    /** The first bytes of each type. */
    private static final Map<String, byte[]> SIGNATURES = Map.of(PDF, PDF_MAGIC, JPEG, JPEG_MAGIC, PNG, PNG_MAGIC);

    static final String COLUMNS = """
            d.id, d.category, d.title, d.document_date, d.year, d.note, d.replaces_id, n.id AS replaced_by, d.filename,
            d.media_type, d.sha256, d.size, d.created_at, d.created_by""";
    static final String FROM = " FROM business_document d LEFT JOIN business_document n ON n.replaces_id = d.id";
    static final String ALL = "SELECT " + COLUMNS + FROM + " ORDER BY d.document_date DESC, d.id DESC";
    static final String ONE = "SELECT " + COLUMNS + FROM + " WHERE d.id = :id";
    static final String FILE = "SELECT filename, media_type, content, sha256, seal FROM business_document WHERE id = :id";
    static final String IDS = "SELECT id FROM business_document ORDER BY id";
    /** The ID comes first: the seal names it. */
    static final String NEXT_ID = "SELECT nextval(pg_get_serial_sequence('business_document', 'id'))";
    static final String INSERT = """
            INSERT INTO business_document (id, category, title, document_date, year, note, replaces_id, filename,
                media_type, content, sha256, size, created_at, created_by, seal)
            OVERRIDING SYSTEM VALUE
            VALUES (:id, :category, :title, :documentDate, :year, :note, :replacesId, :filename, :mediaType, :content,
                :sha256, :size, :createdAt, :createdBy, :seal)""";
    static final String DAMAGED = "Die Datei %s ist beschädigt oder wurde außerhalb der App geändert. Die Seite des "
            + "Dokuments zeigt, was abweicht.";
    static final String WRONG_SHA = "%s: Der Inhalt passt nicht zur gespeicherten Prüfsumme. Gespeichert: %s, "
            + "berechnet: %s.";
    static final String WRONG_SEAL = "%s: Das Siegel passt nicht. Prüfsumme oder Zuordnung der Datei wurden außerhalb "
            + "der App geändert.";
    static final String UPDATE = """
            UPDATE business_document SET category = :category, title = :title, document_date = :documentDate,
                year = :year, note = :note
             WHERE id = :id""";

    private static final String CATEGORY = "category";
    private static final String TITLE = "title";
    private static final String DOCUMENT_DATE = "documentDate";
    private static final String YEAR = "year";
    private static final String NOTE = "note";

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Seal seal;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param jdbc reads and writes the documents
     * @param audit the audit log
     * @param seal seals the files
     * @param clock the time a document is stored
     */
    public DocumentService(JdbcClient jdbc, AuditService audit, Seal seal, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.seal = seal;
        this.clock = clock;
    }

    /**
     * Lists the current version of every document.
     *
     * @return the documents, newest date first
     */
    public List<BusinessDocument> list() {
        return all().stream().filter(BusinessDocument::current).toList();
    }

    /**
     * Lists every version of every document.
     *
     * @return the documents, newest date first
     */
    public List<BusinessDocument> all() {
        return jdbc.sql(ALL).query(DocumentService::row).list();
    }

    /**
     * Returns one document.
     *
     * @param id the ID
     * @return the document, empty if there is none
     */
    public Optional<BusinessDocument> find(long id) {
        return jdbc.sql(ONE).param("id", id).query(DocumentService::row).optional();
    }

    /**
     * Lists the versions of a document: the ones it replaces and the ones that replace it.
     *
     * @param id one version
     * @return every version, newest first; empty if there is none
     */
    public List<BusinessDocument> versions(long id) {
        Map<Long, BusinessDocument> byId = all().stream()
                .collect(Collectors.toMap(BusinessDocument::id, Function.identity()));
        BusinessDocument newest = byId.get(id);
        if (newest == null) {
            return List.of();
        }
        while (newest.replacedBy() != null) {
            newest = byId.get(newest.replacedBy());
        }
        List<BusinessDocument> versions = new ArrayList<>();
        for (BusinessDocument version = newest; version != null;
                version = version.replacesId() == null ? null : byId.get(version.replacesId())) {
            versions.add(version);
        }
        return versions;
    }

    /**
     * Lists every version of the documents of a year, for the archive.
     *
     * @param year the year it concerns, else the year of its date
     * @return the documents, oldest first
     */
    public List<BusinessDocument> ofYear(int year) {
        return all().stream().filter(document -> document.archiveYear() == year)
                .sorted(Comparator.comparing(BusinessDocument::documentDate).thenComparing(BusinessDocument::id))
                .toList();
    }

    /**
     * Returns the years the archive has documents for.
     *
     * @return the archive year of every document
     */
    public Set<Integer> years() {
        return all().stream().map(BusinessDocument::archiveYear).collect(Collectors.toSet());
    }

    /**
     * Returns the file of a document, after it passed the check.
     *
     * @param id the ID
     * @return the file, empty if there is none
     * @throws DocumentException if the file does not match its SHA-256 or seal
     */
    public Optional<DocumentFile> file(long id) {
        return stored(id).map(file -> {
            if (!file.problems().isEmpty()) {
                throw new DocumentException(List.of(DAMAGED.formatted(file.file().filename())));
            }
            return file.file();
        });
    }

    /**
     * Checks the file of a document against its SHA-256 and seal.
     *
     * @param id the ID
     * @return what is wrong, empty if it is in order or there is no such document
     */
    public List<String> problems(long id) {
        return stored(id).map(Stored::problems).orElse(List.of());
    }

    /**
     * Checks every version of every document.
     *
     * @return the documents with a problem
     */
    @Override
    public List<Damage> damaged() {
        List<Damage> damaged = new ArrayList<>();
        for (Long id : jdbc.sql(IDS).query(Long.class).list()) {
            Stored file = stored(id).orElseThrow();
            if (!file.problems().isEmpty()) {
                damaged.add(new Damage(null, "Dokument " + file.file().filename(), "/documents/" + id,
                        file.problems()));
            }
        }
        return damaged;
    }

    /** A file as it is stored, with what its check found. */
    private record Stored(DocumentFile file, List<String> problems) {
    }

    private Optional<Stored> stored(long id) {
        return jdbc.sql(FILE).param("id", id).query((rs, n) -> {
            DocumentFile file = new DocumentFile(rs.getString(1), rs.getString(2), rs.getBytes(3));
            return new Stored(file, check(id, file, rs.getString(4), rs.getString(5)));
        }).optional();
    }

    private List<String> check(long id, DocumentFile file, String sha256, String stored) {
        List<String> problems = new ArrayList<>();
        String computed = Seal.sha256(file.content());
        if (!computed.equals(sha256)) {
            problems.add(WRONG_SHA.formatted(file.filename(), sha256, computed));
        }
        if (!Seal.matches(stored, seal.businessDocument(id, sha256))) {
            problems.add(WRONG_SEAL.formatted(file.filename()));
        }
        return problems;
    }

    /**
     * Stores a new document.
     *
     * @param form its description
     * @param filename the name of the uploaded file, may be {@code null}
     * @param content the bytes
     * @return the stored document
     * @throws DocumentException if the description or the file is not valid
     */
    @Transactional
    public BusinessDocument add(DocumentForm form, String filename, byte[] content) {
        long id = insert(form, null, filename, content);
        BusinessDocument added = find(id).orElseThrow();
        audit.add(AuditAction.DOCUMENT_ADDED, detail(added));
        return added;
    }

    /**
     * Stores a new version of a document. The old one stays, marked as replaced.
     *
     * @param id the current version
     * @param form the description of the new version
     * @param filename the name of the uploaded file, may be {@code null}
     * @param content the bytes
     * @return the new version
     * @throws DocumentException if the document is replaced already, or the description or the file is not valid
     */
    @Transactional
    public BusinessDocument replace(long id, DocumentForm form, String filename, byte[] content) {
        BusinessDocument old = current(id);
        long newId = insert(form, old.id(), filename, content);
        BusinessDocument added = find(newId).orElseThrow();
        audit.add(AuditAction.DOCUMENT_REPLACED, detail(added));
        return added;
    }

    /**
     * Changes the description of a document. Its file stays as it is.
     *
     * @param id the current version
     * @param form the new description
     * @return the changed document
     * @throws DocumentException if the document is replaced already or the description is not valid
     */
    @Transactional
    public BusinessDocument change(long id, DocumentForm form) {
        current(id);
        Description description = describe(form, new ArrayList<>());
        jdbc.sql(UPDATE).param("id", id).param(CATEGORY, description.category().name())
                .param(TITLE, description.title()).param(DOCUMENT_DATE, description.form().getDocumentDate())
                .param(YEAR, description.form().getYear()).param(NOTE, description.note()).update();
        BusinessDocument changed = find(id).orElseThrow();
        audit.add(AuditAction.DOCUMENT_CHANGED, detail(changed));
        return changed;
    }

    private BusinessDocument current(long id) {
        BusinessDocument document = find(id).orElseThrow(() -> new DocumentException(List.of("Dieses Dokument gibt "
                + "es nicht.")));
        if (!document.current()) {
            throw new DocumentException(List.of("Eine neuere Fassung ersetzt dieses Dokument. Bitte die aktuelle "
                    + "Fassung bearbeiten."));
        }
        return document;
    }

    private long insert(DocumentForm form, Long replacesId, String filename, byte[] content) {
        List<String> problems = new ArrayList<>();
        String mediaType = mediaType(problems, content).orElse(null);
        Description description = describe(form, problems);
        long id = jdbc.sql(NEXT_ID).query(Long.class).single();
        String sha256 = Seal.sha256(content);
        jdbc.sql(INSERT).param("id", id).param(CATEGORY, description.category().name())
                .param(TITLE, description.title()).param(DOCUMENT_DATE, form.getDocumentDate())
                .param(YEAR, form.getYear()).param(NOTE, description.note()).param("replacesId", replacesId)
                .param("filename", filename(filename, mediaType)).param("mediaType", mediaType)
                .param("content", content).param("sha256", sha256).param("size", content.length)
                .param("createdAt", Timestamp.from(clock.instant())).param("createdBy", AuditService.currentUser())
                .param("seal", seal.businessDocument(id, sha256)).update();
        return id;
    }

    /** Checks the description; with problems found before, it throws them all. */
    private static Description describe(DocumentForm form, List<String> problems) {
        String title = Fields.text(form.getTitle());
        String note = Fields.text(form.getNote());
        if (form.getCategory() == null) {
            problems.add("Bitte die Art wählen.");
        }
        Fields.required(problems, title, "Titel", MAX_TITLE);
        if (form.getDocumentDate() == null) {
            problems.add("Bitte das Datum angeben.");
        }
        if (form.getYear() != null && (form.getYear() < FIRST_YEAR || form.getYear() > LAST_YEAR)) {
            problems.add("Das Jahr liegt zwischen " + FIRST_YEAR + " und " + LAST_YEAR + ".");
        }
        Fields.optional(problems, note, "Notiz", MAX_NOTE);
        if (!problems.isEmpty()) {
            throw new DocumentException(problems);
        }
        return new Description(form, form.getCategory(), title, note == null ? "" : note);
    }

    /** The checked description. */
    private record Description(DocumentForm form, DocumentCategory category, String title, String note) {
    }

    /**
     * Returns the type of a file from its first bytes, and adds a problem for anything else. Expenses check their
     * receipts with it too.
     *
     * @param problems receives what is wrong
     * @param content the bytes
     * @return PDF, JPEG or PNG; empty for no file, a file over 25 MB or another type
     */
    public static Optional<String> mediaType(List<String> problems, byte[] content) {
        if (content == null || content.length == 0) {
            problems.add("Bitte eine Datei wählen.");
            return Optional.empty();
        }
        if (content.length > MAX_BYTES) {
            problems.add(TOO_LARGE);
            return Optional.empty();
        }
        Optional<String> type = SIGNATURES.entrySet().stream().filter(entry -> startsWith(content, entry.getValue()))
                .map(Map.Entry::getKey).findFirst();
        if (type.isEmpty()) {
            problems.add("Bitte eine PDF-, JPG- oder PNG-Datei wählen.");
        }
        return type;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        return content.length >= magic.length && Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
    }

    /**
     * Returns a safe file name: without a folder and control characters, at most 200 characters with its extension.
     *
     * @param uploaded the name of the upload, may be {@code null}
     * @param mediaType the type of the file
     * @return the name, "Dokument" with the extension of the type if nothing is left
     */
    public static String filename(String uploaded, String mediaType) {
        String name = uploaded == null ? "" : uploaded.substring(Math.max(uploaded.lastIndexOf('/'),
                uploaded.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "Dokument" + EXTENSIONS.get(mediaType);
        }
        int dot = name.lastIndexOf('.');
        String extension = dot != -1 && name.length() - dot <= 10 ? name.substring(dot) : "";
        int kept = Math.min(name.length(), MAX_FILENAME) - extension.length();
        name = name.substring(0, kept) + extension;
        return name;
    }

    private static String detail(BusinessDocument document) {
        return document.category().label() + ": " + document.title() + " (" + document.filename() + ", "
                + document.sha256() + ")";
    }

    private static BusinessDocument row(ResultSet rs, int n) throws SQLException {
        return new BusinessDocument(rs.getLong("id"), DocumentCategory.valueOf(rs.getString(CATEGORY)),
                rs.getString(TITLE), rs.getObject("document_date", LocalDate.class),
                rs.getObject(YEAR, Integer.class), rs.getString(NOTE), rs.getObject("replaces_id", Long.class),
                rs.getObject("replaced_by", Long.class), rs.getString("filename"), rs.getString("media_type"),
                rs.getString("sha256"), rs.getInt("size"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by"));
    }
}
