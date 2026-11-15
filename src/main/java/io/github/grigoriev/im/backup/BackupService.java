package io.github.grigoriev.im.backup;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes all data and documents into one ZIP and reads them back into an empty installation (docs/PLAN.md, section
 * 7, "Reports and export", item 4). Each value travels as PostgreSQL text, binary columns as files of their own, so
 * a table round-trips exactly. The import runs in one transaction and keeps the triggers that freeze issued invoices:
 * an issued invoice goes in as a draft, gets its lines, and only then its status, number and snapshot.
 */
@Service
public class BackupService {

    /** The most an import expands to and the most entries it has: a crafted ZIP must not fill the disk. */
    static final long MAX_BYTES = 4L * 1024 * 1024 * 1024;
    static final int MAX_ENTRIES = 100_000;
    /** The most one file or the manifest expands to: it comes into memory as a whole, a document has 25 MB. */
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    /** The export reads one row at a time: neither the rows nor the files of a table sit in memory together. */
    static final int FETCH_ROWS = 1;

    private static final String INVOICE = "invoice";
    private static final String SCHEMA_KEY = "schema";

    /** The version of the format; an import needs the same one. */
    static final int FORMAT = 1;
    static final String MANIFEST = "manifest.json";

    /** The tables in the order their keys need. Remember-me logins and the Flyway history stay out. */
    static final List<String> TABLES = List.of("country", "app_setting", "tax_note", "number_sequence",
            "threshold_baseline", "seller_profile", "app_user", "user_entities", "user_credentials", "customer",
            "invoice_template", "invoice_template_line", INVOICE, "invoice_line", "invoice_document", "dispatch",
            "payment", "audit_event", "business_document");

    /** The data an installation makes by itself, for example the first admin: the import replaces it. */
    static final List<String> SETUP = List.of("audit_event", "user_credentials", "user_entities", "persistent_logins",
            "app_user", "threshold_baseline", "number_sequence", "tax_note", "app_setting", "country");

    /** The data a user enters: the import needs it empty. */
    static final List<String> BUSINESS = List.of("seller_profile", "customer", "invoice_template", INVOICE,
            "business_document");

    /** The columns of an invoice that the import sets after its lines, so the triggers see a draft until then. */
    static final List<String> LATER = List.of("status", "number", "issued_at", "snapshot", "cancels_id", "template_id");

    static final String SCHEMA = "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC "
            + "LIMIT 1";
    /** The full type of each column, with its length: a cast to plain "character" would cut a CHAR(64) to one. */
    static final String TYPES = """
            SELECT attname, format_type(atttypid, atttypmod) FROM pg_attribute
             WHERE attrelid = to_regclass(:table) AND attnum > 0 AND NOT attisdropped ORDER BY attnum""";
    static final String IDENTITY = """
            SELECT column_name FROM information_schema.columns
             WHERE table_schema = current_schema() AND table_name = :table AND is_identity = 'YES'""";
    static final String BINARY = "bytea";

    /** A column name of this schema: the import puts only such names into SQL, and only if the table has them. */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param jdbc reads and writes the tables
     * @param json writes and reads the manifest and the rows
     * @param audit the audit log
     * @param clock the time of an export
     */
    public BackupService(JdbcClient jdbc, JsonMapper json, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Writes the export. One snapshot of the database serves all tables, so they fit together.
     *
     * @param out where the ZIP goes; it is closed at the end
     * @throws UncheckedIOException if the output fails
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void export(OutputStream out) {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, MANIFEST, json.writeValueAsBytes(Map.of("format", FORMAT, SCHEMA_KEY, schema(),
                    "exported", clock.instant().toString(), "tables", TABLES)));
            for (String table : TABLES) {
                exportTable(zip, table);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        audit.add(AuditAction.DATA_EXPORTED, "");
    }

    /**
     * Reads an export into this installation. It needs the same format and schema version, and no seller profile,
     * customer, template or invoice yet. Users, settings and the audit log of the export replace those here. The rows
     * stream from the file, so an export of any size needs the memory of one document only.
     *
     * @param file the ZIP on disk
     * @throws BackupException if the file is no export, of another version, or this installation has data
     */
    @Transactional
    public void restore(Path file) {
        BackupArchive.read(file, json, MAX_BYTES, MAX_FILE_BYTES, MAX_ENTRIES, this::restore);
    }

    private void restore(BackupArchive archive) {
        Map<String, Object> manifest = archive.value(MANIFEST, new TypeReference<>() { });
        if (!Integer.valueOf(FORMAT).equals(manifest.get("format"))) {
            throw new BackupException("Diese Datei ist keine Datensicherung dieser App.");
        }
        if (!Objects.equals(manifest.get(SCHEMA_KEY), schema())) {
            throw new BackupException("Die Datensicherung stammt von Version " + manifest.get(SCHEMA_KEY)
                    + " der Datenbank, diese App hat Version " + schema() + ". Bitte dieselbe Version verwenden.");
        }
        // Nobody writes into these tables until the import commits: no customer can slip in between check and import.
        jdbc.sql("LOCK TABLE " + String.join(", ", TABLES) + ", persistent_logins IN EXCLUSIVE MODE").update();
        for (String table : BUSINESS) {
            if (jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + ")").query(Boolean.class).single()) {
                throw new BackupException("Hier gibt es schon Daten. Eine Datensicherung lässt sich nur in eine leere "
                        + "Installation einspielen.");
            }
        }
        SETUP.forEach(table -> jdbc.sql("DELETE FROM " + table).update());
        for (String table : TABLES) {
            Map<String, String> types = types(table);
            archive.rows(data(table), row -> insert(table, types, archive, INVOICE.equals(table) ? asDraft(row) : row));
        }
        // A second pass over the invoices, after their lines and documents: the rows never sit in memory together.
        Map<String, String> invoiceTypes = types(INVOICE);
        archive.rows(data(INVOICE), invoice -> finish(invoiceTypes, invoice));
        TABLES.forEach(this::resetIdentity);
        audit.add(AuditAction.DATA_IMPORTED, "");
    }

    /**
     * Writes the rows of a table as one JSON entry, a row at a time, and then the files of its binary columns, one at
     * a time. Both passes read in the order of the first column, which is unique in every table with files.
     */
    private void exportTable(ZipOutputStream zip, String table) throws IOException {
        Map<String, String> types = types(table);
        List<String> binary = types.keySet().stream().filter(column -> BINARY.equals(types.get(column))).toList();
        String order = " FROM " + table + " ORDER BY " + types.keySet().iterator().next();
        String columns = types.keySet().stream()
                .map(column -> binary.contains(column) ? column + " IS NOT NULL AS " + column : column)
                .collect(Collectors.joining(", "));
        zip.putNextEntry(new ZipEntry(data(table)));
        AtomicInteger index = new AtomicInteger();
        try (JsonGenerator rows = json.writer().without(StreamWriteFeature.AUTO_CLOSE_TARGET).createGenerator(zip)) {
            rows.writeStartArray();
            jdbc.sql("SELECT " + columns + order).withFetchSize(FETCH_ROWS).query(rs -> {
                Map<String, Object> row = new LinkedHashMap<>();
                int i = 1;
                for (String column : types.keySet()) {
                    boolean file = binary.contains(column);
                    row.put(column, file ? reference(rs.getBoolean(i), table, index.get(), column) : rs.getString(i));
                    i++;
                }
                rows.writePOJO(row);
                index.incrementAndGet();
            });
            rows.writeEndArray();
        }
        if (!binary.isEmpty()) {
            index.set(0);
            jdbc.sql("SELECT " + String.join(", ", binary) + order).withFetchSize(FETCH_ROWS).query(rs -> {
                for (int i = 0; i < binary.size(); i++) {
                    byte[] content = rs.getBytes(i + 1);
                    if (content != null) {
                        file(zip, fileName(table, index.get(), binary.get(i)), content);
                    }
                }
                index.incrementAndGet();
            });
        }
    }

    /** The JSON value of a binary column: the name of its file, or {@code null} without content. */
    private static Object reference(boolean present, String table, int row, String column) {
        return present ? Map.of("file", fileName(table, row, column)) : null;
    }

    private static String fileName(String table, int row, String column) {
        return "files/" + table + "/" + row + "-" + column;
    }

    private String schema() {
        return jdbc.sql(SCHEMA).query(String.class).single();
    }

    private Map<String, String> types(String table) {
        Map<String, String> types = new LinkedHashMap<>();
        jdbc.sql(TYPES).param("table", table).query(rs -> {
            types.put(rs.getString(1), rs.getString(2));
        });
        return types;
    }

    private static String data(String table) {
        return "data/" + table + ".json";
    }

    private void insert(String table, Map<String, String> types, BackupArchive archive, Map<String, Object> row) {
        List<String> columns = new ArrayList<>(row.keySet());
        List<String> values = columns.stream().map(column -> value(types, column)).toList();
        JdbcClient.StatementSpec insert = jdbc.sql("INSERT INTO " + table + " (" + String.join(", ", columns)
                + ") OVERRIDING SYSTEM VALUE VALUES (" + String.join(", ", values) + ")");
        for (String column : columns) {
            insert = insert.param(column, parameter(archive, row.get(column)));
        }
        insert.update();
    }

    /** An issued invoice goes in as a draft; {@link #finish} gives it its status, number and snapshot. */
    private static Map<String, Object> asDraft(Map<String, Object> row) {
        Map<String, Object> draft = new LinkedHashMap<>(row);
        LATER.forEach(column -> draft.put(column, null));
        draft.put("status", "DRAFT");
        return draft;
    }

    private void finish(Map<String, String> types, Map<String, Object> invoice) {
        List<String> sets = LATER.stream().map(column -> column + " = " + value(types, column)).toList();
        JdbcClient.StatementSpec update = jdbc.sql("UPDATE invoice SET " + String.join(", ", sets)
                + " WHERE id = CAST(:id AS BIGINT)").param("id", invoice.get("id"));
        for (String column : LATER) {
            update = update.param(column, invoice.get(column));
        }
        update.update();
    }

    private void resetIdentity(String table) {
        for (String column : jdbc.sql(IDENTITY).param("table", table).query(String.class).list()) {
            jdbc.sql("SELECT setval(pg_get_serial_sequence('" + table + "', '" + column + "'), "
                    + "COALESCE((SELECT max(" + column + ") FROM " + table + "), 1), "
                    + "(SELECT max(" + column + ") FROM " + table + ") IS NOT NULL)").query(Long.class).single();
        }
    }

    /** A placeholder that reads the text of the export as the column's type; a binary value goes in as it is. */
    private static String value(Map<String, String> types, String column) {
        String type = types.get(column);
        if (type == null || !IDENTIFIER.matcher(column).matches()) {
            throw new BackupException("Die Datensicherung passt nicht zu dieser Datenbank: Spalte " + column + ".");
        }
        return BINARY.equals(type) ? ":" + column : "CAST(:" + column + " AS " + type + ")";
    }

    private static Object parameter(BackupArchive archive, Object value) {
        return value instanceof Map<?, ?> file ? archive.file(String.valueOf(file.get("file"))) : value;
    }

    /** Writes a file while its row is read; the row reader allows no checked exception. */
    private static void file(ZipOutputStream zip, String name, byte[] content) {
        try {
            entry(zip, name, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
    }
}
