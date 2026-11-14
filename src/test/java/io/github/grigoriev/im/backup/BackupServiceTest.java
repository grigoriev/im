package io.github.grigoriev.im.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import tools.jackson.databind.json.JsonMapper;

/**
 * The export and the import with a fake database: tables as lists of rows, the types of their columns, and a log of
 * every statement that writes.
 */
class BackupServiceTest {

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AuditService audit = mock(AuditService.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final BackupService service = new BackupService(jdbc, json, audit,
            Clock.fixed(Instant.parse("2027-03-18T08:00:00Z"), ZoneOffset.UTC));

    /** Rows per table, each column a pair of type and value. */
    private final Map<String, List<Map<String, Object[]>>> tables = new HashMap<>();
    private final Map<String, Boolean> filled = new HashMap<>();
    private final List<String> identities = new ArrayList<>(List.of("customer", "invoice"));
    private final List<Map<String, Object>> writes = new ArrayList<>();
    private String schema = "20";

    private void row(String table, Object... typeNameValue) {
        Map<String, Object[]> row = new LinkedHashMap<>();
        for (int i = 0; i < typeNameValue.length; i += 3) {
            row.put((String) typeNameValue[i + 1], new Object[] {typeNameValue[i], typeNameValue[i + 2]});
        }
        tables.computeIfAbsent(table, key -> new ArrayList<>()).add(row);
    }

    @SuppressWarnings("unchecked")
    private StatementSpec statement(String sql) {
        StatementSpec spec = mock(StatementSpec.class);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sql", sql);
        when(spec.param(anyString(), any())).thenAnswer(call -> {
            params.put(call.getArgument(0), call.getArgument(1));
            return spec;
        });
        when(spec.update()).thenAnswer(call -> {
            writes.add(params);
            return 1;
        });
        MappedQuerySpec<String> strings = mock(MappedQuerySpec.class);
        when(spec.query(String.class)).thenReturn(strings);
        when(strings.single()).thenAnswer(call -> schema);
        when(strings.list()).thenAnswer(call -> identities.contains((String) params.get("table")) ? List.of("id")
                : List.of());
        MappedQuerySpec<Boolean> exists = mock(MappedQuerySpec.class);
        when(spec.query(Boolean.class)).thenReturn(exists);
        when(exists.single()).thenAnswer(call -> filled.getOrDefault(sql, false));
        MappedQuerySpec<Long> longs = mock(MappedQuerySpec.class);
        when(spec.query(Long.class)).thenReturn(longs);
        when(longs.single()).thenAnswer(call -> {
            writes.add(params);
            return 1L;
        });
        org.mockito.Mockito.doAnswer(call -> {
            handle(sql, params, call.getArgument(0));
            return null;
        }).when(spec).query(any(RowCallbackHandler.class));
        // The fake answers every query of the service; nothing else asks it.
        return spec;
    }

    /** Runs the handler over the rows of a table, or over the column types of a table. */
    private void handle(String sql, Map<String, Object> params, RowCallbackHandler handler) throws Exception {
        if (sql.equals(BackupService.TYPES)) {
            Map<String, String> types = new LinkedHashMap<>();
            tables.getOrDefault((String) params.get("table"), List.of()).forEach(row ->
                    row.forEach((column, typeValue) -> types.put(column, (String) typeValue[0])));
            if ("invoice".equals(params.get("table"))) {
                BackupService.LATER.forEach(column -> types.putIfAbsent(column, "text"));
            }
            for (Map.Entry<String, String> type : types.entrySet()) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString(1)).thenReturn(type.getKey());
                when(rs.getString(2)).thenReturn(type.getValue());
                handler.processRow(rs);
            }
            return;
        }
        String table = sql.replace("SELECT * FROM ", "").replace(" ORDER BY 1", "");
        for (Map<String, Object[]> row : tables.getOrDefault(table, List.of())) {
            List<String> columns = new ArrayList<>(row.keySet());
            ResultSetMetaData meta = mock(ResultSetMetaData.class);
            when(meta.getColumnCount()).thenReturn(columns.size());
            ResultSet rs = mock(ResultSet.class);
            when(rs.getMetaData()).thenReturn(meta);
            for (int i = 1; i <= columns.size(); i++) {
                Object[] typeValue = row.get(columns.get(i - 1));
                when(meta.getColumnName(i)).thenReturn(columns.get(i - 1));
                when(meta.getColumnTypeName(i)).thenReturn(((String) typeValue[0]).equals("bytea") ? "bytea" : "text");
                if (typeValue[1] instanceof byte[] bytes) {
                    when(rs.getBytes(i)).thenReturn(bytes);
                } else {
                    when(rs.getString(i)).thenReturn((String) typeValue[1]);
                }
            }
            handler.processRow(rs);
        }
    }

    @BeforeEach
    void database() {
        when(jdbc.sql(anyString())).thenAnswer(call -> statement(call.getArgument(0)));
        row("customer", "bigint", "id", "7", "character varying(200)", "name", "Verlag \"am\" Fluss");
        row("invoice", "bigint", "id", "3", "character varying(16)", "status", "ISSUED", "character varying(32)",
                "number", "2027-01", "integer", "customer_id", "7", "timestamp with time zone", "issued_at",
                "2027-03-18 08:00:00+00", "text", "snapshot", "{}", "bigint", "cancels_id", null, "bigint", "template_id",
                null);
        row("invoice_document", "bigint", "id", "1", "character(64)", "sha256", "ab", "bytea", "content",
                new byte[] {37, 80, 68, 70});
        row("invoice_document", "bigint", "id", "2", "character(64)", "sha256", "cd", "bytea", "content", null);
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
            }
        }
        return out.toByteArray();
    }

    private byte[] export() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(out);
        return out.toByteArray();
    }

    private static String text(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    @Test
    void exportsEveryTableAsTextAndEveryBinaryAsAFile() throws IOException {
        Map<String, byte[]> entries = unzip(export());
        assertThat(entries).containsKey("manifest.json");
        assertThat(json.readTree(entries.get("manifest.json")).get("format").asInt()).isEqualTo(1);
        assertThat(json.readTree(entries.get("manifest.json")).get("schema").asString()).isEqualTo("20");
        assertThat(json.readTree(entries.get("manifest.json")).get("exported").asString()).isEqualTo("2027-03-18T08:00:00Z");
        for (String table : BackupService.TABLES) {
            assertThat(entries).containsKey("data/" + table + ".json");
        }
        assertThat(text(entries.get("data/customer.json"))).isEqualTo("[{\"id\":\"7\",\"name\":\"Verlag \\\"am\\\" Fluss\"}]");
        assertThat(text(entries.get("data/invoice_document.json"))).isEqualTo("[{\"id\":\"1\",\"sha256\":\"ab\","
                + "\"content\":{\"file\":\"files/invoice_document/0-content\"}},{\"id\":\"2\",\"sha256\":\"cd\","
                + "\"content\":null}]");
        assertThat(entries.get("files/invoice_document/0-content")).containsExactly(37, 80, 68, 70);
        assertThat(text(entries.get("data/payment.json"))).isEqualTo("[]");
        verify(audit).add(AuditAction.DATA_EXPORTED, "");
    }

    @Test
    void aBrokenOutputFailsTheExport() {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };
        assertThatThrownBy(() -> service.export(broken)).isInstanceOf(UncheckedIOException.class);
        verify(audit, never()).add(AuditAction.DATA_EXPORTED, "");
    }

    @Test
    void importsIntoAnEmptyInstallationWithTheInvoiceAsDraftFirst() {
        byte[] export = export();
        service.restore(new ByteArrayInputStream(export));

        List<String> sql = writes.stream().map(write -> (String) write.get("sql")).toList();
        assertThat(sql.getFirst()).as("the tables are locked before the checks").isEqualTo("LOCK TABLE "
                + String.join(", ", BackupService.TABLES) + ", persistent_logins IN EXCLUSIVE MODE");
        assertThat(sql.subList(1, BackupService.SETUP.size() + 1)).containsExactlyElementsOf(
                BackupService.SETUP.stream().map(table -> "DELETE FROM " + table).toList());
        assertThat(sql).contains("INSERT INTO customer (id, name) OVERRIDING SYSTEM VALUE VALUES (CAST(:id AS bigint), "
                + "CAST(:name AS character varying(200)))");
        Map<String, Object> invoice = writes.stream().filter(write -> ((String) write.get("sql"))
                .startsWith("INSERT INTO invoice ")).findFirst().orElseThrow();
        assertThat(invoice).containsEntry("status", "DRAFT").containsEntry("number", null)
                .containsEntry("issued_at", null).containsEntry("snapshot", null).containsEntry("customer_id", "7");
        Map<String, Object> document = writes.stream().filter(write -> ((String) write.get("sql"))
                .startsWith("INSERT INTO invoice_document ")).findFirst().orElseThrow();
        assertThat((String) document.get("sql")).contains("CAST(:sha256 AS character(64))").contains(":content)");
        assertThat((byte[]) document.get("content")).containsExactly(37, 80, 68, 70);
        Map<String, Object> finish = writes.stream().filter(write -> ((String) write.get("sql"))
                .startsWith("UPDATE invoice SET ")).findFirst().orElseThrow();
        assertThat(finish).containsEntry("id", "3").containsEntry("status", "ISSUED").containsEntry("number", "2027-01")
                .containsEntry("issued_at", "2027-03-18 08:00:00+00").containsEntry("snapshot", "{}");
        assertThat((String) finish.get("sql")).contains("status = CAST(:status AS character varying(16))")
                .endsWith("WHERE id = CAST(:id AS BIGINT)");
        assertThat(sql.indexOf(finish.get("sql"))).as("after the documents").isGreaterThan(sql.indexOf(document.get("sql")));
        assertThat(sql.stream().filter(statement -> statement.startsWith("SELECT setval"))).containsExactly(
                "SELECT setval(pg_get_serial_sequence('customer', 'id'), COALESCE((SELECT max(id) FROM customer), 1), "
                        + "(SELECT max(id) FROM customer) IS NOT NULL)",
                "SELECT setval(pg_get_serial_sequence('invoice', 'id'), COALESCE((SELECT max(id) FROM invoice), 1), "
                        + "(SELECT max(id) FROM invoice) IS NOT NULL)");
        verify(audit).add(AuditAction.DATA_IMPORTED, "");
    }

    private void refused(byte[] zip, String message) {
        writes.clear();
        ByteArrayInputStream in = new ByteArrayInputStream(zip);
        assertThatThrownBy(() -> service.restore(in)).isInstanceOf(BackupException.class).hasMessage(message);
        assertThat(writes).as("nothing but the lock").allSatisfy(write -> assertThat((String) write.get("sql"))
                .startsWith("LOCK TABLE"));
    }

    @Test
    void refusesAnotherVersionOrAnInstallationWithData() throws IOException {
        Map<String, byte[]> entries = unzip(export());
        entries.put("manifest.json", "{\"format\":2,\"schema\":\"20\"}".getBytes(StandardCharsets.UTF_8));
        refused(zip(entries), "Diese Datei ist keine Datensicherung dieser App.");
        entries.put("manifest.json", "{\"format\":1,\"schema\":\"19\"}".getBytes(StandardCharsets.UTF_8));
        refused(zip(entries), "Die Datensicherung stammt von Version 19 der Datenbank, diese App hat Version 20. "
                + "Bitte dieselbe Version verwenden.");
        byte[] fine = export();
        filled.put("SELECT EXISTS (SELECT 1 FROM invoice)", true);
        refused(fine, "Hier gibt es schon Daten. Eine Datensicherung lässt sich nur in eine leere Installation "
                + "einspielen.");
        verify(audit, never()).add(AuditAction.DATA_IMPORTED, "");
    }

    @Test
    void refusesAFileThatIsNoExport() throws IOException {
        refused("kein zip".getBytes(StandardCharsets.UTF_8), "Diese Datei ist keine Datensicherung dieser App: "
                + "manifest.json fehlt.");
        refused(zip(Map.of("manifest.json", "{kaputt".getBytes(StandardCharsets.UTF_8))),
                "Die Datensicherung ist beschädigt: manifest.json.");
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("lost");
            }
        };
        assertThatThrownBy(() -> service.restore(broken)).isInstanceOf(BackupException.class)
                .hasMessage("Diese Datei ist kein ZIP.");
        assertThat(BackupService.MAX_BYTES).isEqualTo(512L * 1024 * 1024);
        assertThat(BackupService.MAX_ENTRIES).isEqualTo(100_000);
    }

    @Test
    void refusesAnExportWithAMissingPartOrColumn() throws IOException {
        Map<String, byte[]> entries = unzip(export());
        entries.remove("files/invoice_document/0-content");
        ByteArrayInputStream noFile = new ByteArrayInputStream(zip(entries));
        assertThatThrownBy(() -> service.restore(noFile)).isInstanceOf(BackupException.class)
                .hasMessage("In der Datensicherung fehlt die Datei files/invoice_document/0-content.");
        Map<String, byte[]> noData = unzip(export());
        noData.remove("data/payment.json");
        ByteArrayInputStream noTable = new ByteArrayInputStream(zip(noData));
        assertThatThrownBy(() -> service.restore(noTable)).isInstanceOf(BackupException.class)
                .hasMessage("Diese Datei ist keine Datensicherung dieser App: data/payment.json fehlt.");
        Map<String, byte[]> foreign = unzip(export());
        foreign.put("data/customer.json", "[{\"id\":\"8\",\"spitzname\":\"x\"}]".getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream unknown = new ByteArrayInputStream(zip(foreign));
        assertThatThrownBy(() -> service.restore(unknown)).isInstanceOf(BackupException.class)
                .hasMessage("Die Datensicherung passt nicht zu dieser Datenbank: Spalte spitzname.");
        row("customer", "text", "Bad Name", "x");
        ByteArrayInputStream odd = new ByteArrayInputStream(zip(unzip(export())));
        assertThatThrownBy(() -> service.restore(odd)).isInstanceOf(BackupException.class)
                .as("a column name that is no plain identifier never reaches SQL")
                .hasMessage("Die Datensicherung passt nicht zu dieser Datenbank: Spalte Bad Name.");
    }

    @Test
    void refusesAZipThatExpandsTooFar() throws IOException {
        byte[] two = zip(Map.of("a", new byte[10], "b", new byte[10]));
        assertThat(BackupService.read(new ByteArrayInputStream(two), 20, 2)).containsOnlyKeys("a", "b");
        ByteArrayInputStream big = new ByteArrayInputStream(two);
        assertThatThrownBy(() -> BackupService.read(big, 19, 2)).hasMessage(BackupService.TOO_BIG);
        ByteArrayInputStream many = new ByteArrayInputStream(two);
        assertThatThrownBy(() -> BackupService.read(many, 20, 1)).hasMessage(BackupService.TOO_BIG);
    }
}
