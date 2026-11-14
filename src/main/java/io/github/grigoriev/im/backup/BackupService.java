package io.github.grigoriev.im.backup;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.sql.ResultSetMetaData;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
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

    /** The version of the format; an import needs the same one. */
    static final int FORMAT = 1;
    static final String MANIFEST = "manifest.json";

    /** The tables in the order their keys need. Remember-me logins and the Flyway history stay out. */
    static final List<String> TABLES = List.of("country", "app_setting", "tax_note", "number_sequence",
            "threshold_baseline", "seller_profile", "app_user", "user_entities", "user_credentials", "customer",
            "invoice_template", "invoice_template_line", "invoice", "invoice_line", "invoice_document", "dispatch",
            "payment", "audit_event");

    /** The data an installation makes by itself, for example the first admin: the import replaces it. */
    static final List<String> SETUP = List.of("audit_event", "user_credentials", "user_entities", "persistent_logins",
            "app_user", "threshold_baseline", "number_sequence", "tax_note", "app_setting", "country");

    /** The data a user enters: the import needs it empty. */
    static final List<String> BUSINESS = List.of("seller_profile", "customer", "invoice_template", "invoice");

    /** The columns of an invoice that the import sets after its lines, so the triggers see a draft until then. */
    static final List<String> LATER = List.of("status", "number", "issued_at", "snapshot", "cancels_id", "template_id");

    static final String SCHEMA = """
            SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1""";
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
            entry(zip, MANIFEST, json.writeValueAsBytes(Map.of("format", FORMAT, "schema", schema(),
                    "exported", clock.instant().toString(), "tables", TABLES)));
            for (String table : TABLES) {
                Map<String, byte[]> files = new LinkedHashMap<>();
                List<Map<String, Object>> rows = new ArrayList<>();
                jdbc.sql("SELECT * FROM " + table + " ORDER BY 1").query(rs -> {
                    ResultSetMetaData meta = rs.getMetaData();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String column = meta.getColumnName(i);
                        if (BINARY.equals(meta.getColumnTypeName(i)) && rs.getBytes(i) != null) {
                            String name = "files/" + table + "/" + rows.size() + "-" + column;
                            files.put(name, rs.getBytes(i));
                            row.put(column, Map.of("file", name));
                        } else {
                            row.put(column, rs.getString(i));
                        }
                    }
                    rows.add(row);
                });
                entry(zip, "data/" + table + ".json", json.writeValueAsBytes(rows));
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    entry(zip, file.getKey(), file.getValue());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        audit.add(AuditAction.DATA_EXPORTED, "");
    }

    /**
     * Reads an export into this installation. It needs the same format and schema version, and no seller profile,
     * customer, template or invoice yet. Users, settings and the audit log of the export replace those here.
     *
     * @param in the ZIP
     * @throws BackupException if the file is no export, of another version, or this installation has data
     */
    @Transactional
    public void restore(InputStream in) {
        Map<String, byte[]> entries = read(in);
        Map<String, Object> manifest = parse(entries, MANIFEST, new TypeReference<>() { });
        if (!Integer.valueOf(FORMAT).equals(manifest.get("format"))) {
            throw new BackupException("Diese Datei ist keine Datensicherung dieser App.");
        }
        if (!Objects.equals(manifest.get("schema"), schema())) {
            throw new BackupException("Die Datensicherung stammt von Version " + manifest.get("schema")
                    + " der Datenbank, diese App hat Version " + schema() + ". Bitte dieselbe Version verwenden.");
        }
        for (String table : BUSINESS) {
            if (jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + ")").query(Boolean.class).single()) {
                throw new BackupException("Hier gibt es schon Daten. Eine Datensicherung lässt sich nur in eine leere "
                        + "Installation einspielen.");
            }
        }
        SETUP.forEach(table -> jdbc.sql("DELETE FROM " + table).update());
        List<Map<String, Object>> invoices = List.of();
        for (String table : TABLES) {
            Map<String, String> types = types(table);
            List<Map<String, Object>> rows = parse(entries, "data/" + table + ".json", new TypeReference<>() { });
            for (Map<String, Object> row : rows) {
                insert(table, types, entries, "invoice".equals(table) ? asDraft(row) : row);
            }
            if ("invoice".equals(table)) {
                invoices = rows;
            }
        }
        Map<String, String> invoiceTypes = types("invoice");
        for (Map<String, Object> invoice : invoices) {
            finish(invoiceTypes, invoice);
        }
        TABLES.forEach(this::resetIdentity);
        audit.add(AuditAction.DATA_IMPORTED, "");
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

    private void insert(String table, Map<String, String> types, Map<String, byte[]> entries, Map<String, Object> row) {
        List<String> columns = new ArrayList<>(row.keySet());
        List<String> values = columns.stream().map(column -> value(types, column)).toList();
        JdbcClient.StatementSpec insert = jdbc.sql("INSERT INTO " + table + " (" + String.join(", ", columns)
                + ") OVERRIDING SYSTEM VALUE VALUES (" + String.join(", ", values) + ")");
        for (String column : columns) {
            insert = insert.param(column, parameter(entries, row.get(column)));
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

    private static Object parameter(Map<String, byte[]> entries, Object value) {
        if (value instanceof Map<?, ?> file) {
            byte[] content = entries.get(String.valueOf(file.get("file")));
            if (content == null) {
                throw new BackupException("In der Datensicherung fehlt die Datei " + file.get("file") + ".");
            }
            return content;
        }
        return value;
    }

    private <T> T parse(Map<String, byte[]> entries, String name, TypeReference<T> type) {
        byte[] content = entries.get(name);
        if (content == null) {
            throw new BackupException("Diese Datei ist keine Datensicherung dieser App: " + name + " fehlt.");
        }
        try {
            return json.readValue(content, type);
        } catch (RuntimeException e) {
            throw new BackupException("Die Datensicherung ist beschädigt: " + name + ".");
        }
    }

    private static Map<String, byte[]> read(InputStream in) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        } catch (IOException e) {
            throw new BackupException("Diese Datei ist kein ZIP.");
        }
        return entries;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
    }
}
