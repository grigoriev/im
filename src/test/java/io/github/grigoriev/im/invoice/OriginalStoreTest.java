package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.integrity.Seal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class OriginalStoreTest {

    private static final Instant NOW = Instant.parse("2026-12-01T08:00:00Z");
    private static final byte[] PDF = "%PDF-1.7 Rechnung".getBytes(StandardCharsets.US_ASCII);

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final Seal seal = new Seal("test-only-integrity-key-0123456789abcdef");
    private final OriginalStore store = new OriginalStore(jdbc, seal);

    /** The table in memory: one map of columns per row, in the order of the IDs. */
    private final Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();

    @BeforeEach
    void table() {
        for (String sql : List.of(OriginalStore.NEXT_ID, OriginalStore.INSERT, OriginalStore.OF_INVOICE,
                OriginalStore.ONE, OriginalStore.SUMMARIES)) {
            StatementSpec spec = statement(sql);
            when(jdbc.sql(sql)).thenReturn(spec);
        }
    }

    @SuppressWarnings("unchecked")
    private StatementSpec statement(String sql) {
        StatementSpec spec = mock(StatementSpec.class);
        Map<String, Object> params = new HashMap<>();
        when(spec.param(anyString(), any())).thenAnswer(call -> {
            params.put(call.getArgument(0), call.getArgument(1));
            return spec;
        });
        when(spec.query(any(RowMapper.class))).thenAnswer(call -> {
            RowMapper<Object> mapper = call.getArgument(0);
            MappedQuerySpec<Object> query = mock(MappedQuerySpec.class);
            when(query.list()).thenAnswer(list -> select(sql, params, mapper));
            when(query.optional()).thenAnswer(one -> select(sql, params, mapper).stream().findFirst());
            return query;
        });
        MappedQuerySpec<Long> id = mock(MappedQuerySpec.class);
        when(spec.query(Long.class)).thenReturn(id);
        when(id.single()).thenAnswer(call -> rows.size() + 1L);
        when(spec.update()).thenAnswer(call -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", params.get("id"));
            row.put("invoice_id", params.get("invoiceId"));
            row.put("filename", params.get("filename"));
            row.put("media_type", params.get("mediaType"));
            row.put("content", params.get("content"));
            row.put("sha256", params.get("sha256"));
            row.put("size", params.get("size"));
            row.put("seal", params.get("seal"));
            row.put("created_at", params.get("createdAt"));
            row.put("created_by", params.get("createdBy"));
            rows.put((Long) params.get("id"), row);
            return 1;
        });
        return spec;
    }

    private List<Object> select(String sql, Map<String, Object> params, RowMapper<Object> mapper) throws SQLException {
        List<Object> found = new ArrayList<>();
        int n = 0;
        for (Map<String, Object> row : rows.values()) {
            boolean ofInvoice = row.get("invoice_id").equals(params.get("invoiceId"));
            boolean all = sql.equals(OriginalStore.OF_INVOICE) || sql.equals(OriginalStore.SUMMARIES);
            if (ofInvoice && (all || row.get("id").equals(params.get("id")))) {
                ResultSet rs = resultSet(row);
                if (sql.equals(OriginalStore.SUMMARIES)) {
                    // The list leaves the bytes in the database: a read of them fails.
                    when(rs.getBytes("content")).thenThrow(new SQLException("content is not selected"));
                }
                found.add(mapper.mapRow(rs, n++));
            }
        }
        return found;
    }

    private static ResultSet resultSet(Map<String, Object> row) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn((Long) row.get("id"));
        when(rs.getLong("invoice_id")).thenReturn((Long) row.get("invoice_id"));
        when(rs.getString("filename")).thenReturn((String) row.get("filename"));
        when(rs.getString("media_type")).thenReturn((String) row.get("media_type"));
        when(rs.getBytes("content")).thenReturn((byte[]) row.get("content"));
        when(rs.getString("sha256")).thenReturn((String) row.get("sha256"));
        when(rs.getInt("size")).thenReturn((Integer) row.get("size"));
        when(rs.getString("seal")).thenReturn((String) row.get("seal"));
        when(rs.getTimestamp("created_at")).thenReturn((Timestamp) row.get("created_at"));
        when(rs.getString("created_by")).thenReturn((String) row.get("created_by"));
        return rs;
    }

    private static byte[] zip(String... names) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
            }
        }
        return out.toByteArray();
    }

    private static Optional<String> type(byte[] content, List<String> problems) {
        return OriginalStore.mediaType(problems, content);
    }

    @Test
    void tellsTheTypeFromTheBytes() throws IOException {
        List<String> problems = new ArrayList<>();
        assertThat(type(PDF, problems)).contains(OriginalStore.PDF);
        assertThat(type(zip("[Content_Types].xml", "xl/workbook.xml"), problems)).contains(OriginalStore.XLSX);
        assertThat(type(zip("[Content_Types].xml", "word/document.xml"), problems)).contains(OriginalStore.DOCX);
        assertThat(type("<?xml version=\"1.0\"?><Invoice/>".getBytes(StandardCharsets.UTF_8), problems))
                .contains(OriginalStore.XML);
        assertThat(type("\uFEFF \n<rsm:CrossIndustryInvoice/>".getBytes(StandardCharsets.UTF_8), problems))
                .as("byte order mark and white space before the first tag").contains(OriginalStore.XML);
        assertThat(problems).isEmpty();
    }

    @Test
    void refusesNoFileALargeOneAndOtherTypes() throws IOException {
        List<String> problems = new ArrayList<>();
        assertThat(type(null, problems)).isEmpty();
        assertThat(type(new byte[0], problems)).isEmpty();
        assertThat(type(new byte[DocumentService.MAX_BYTES + 1], problems)).isEmpty();
        assertThat(problems).containsExactly(OriginalStore.NO_FILE, OriginalStore.NO_FILE, DocumentService.TOO_LARGE);
        problems.clear();
        assertThat(type(zip("mimetype", "content.xml"), problems)).as("an OpenDocument file").isEmpty();
        assertThat(type("Rechnung".getBytes(StandardCharsets.UTF_8), problems)).isEmpty();
        assertThat(type("<".getBytes(StandardCharsets.UTF_8), problems)).isEmpty();
        assertThat(type("<1".getBytes(StandardCharsets.UTF_8), problems)).isEmpty();
        assertThat(type("   ".getBytes(StandardCharsets.UTF_8), problems)).isEmpty();
        assertThat(problems).hasSize(5).containsOnly(OriginalStore.WRONG_TYPE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void readsNoNameBeyondTheEndOfTheFile(int cut) throws IOException {
        byte[] full = zip("xl/workbook.xml");
        // The central directory names the entry near the end; cut into its name, or keep it whole.
        int name = indexOf(full, "xl/workbook.xml".getBytes(StandardCharsets.UTF_8), 40);
        byte[] cutOff = Arrays.copyOf(full, cut == 0 ? full.length : name + 15 - cut);
        assertThat(OriginalStore.office(cutOff)).isEqualTo(cut == 0 ? Optional.of(OriginalStore.XLSX)
                : Optional.empty());
    }

    /** A central directory header that names {@code xl/workbook.xml}, with a length of the name of 15 + 256 × high. */
    private static byte[] central(int high, int tail) {
        byte[] name = "xl/workbook.xml".getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[46 + name.length + tail];
        header[0] = 'P';
        header[1] = 'K';
        header[2] = 1;
        header[3] = 2;
        header[28] = (byte) name.length;
        header[29] = (byte) high;
        System.arraycopy(name, 0, header, 46, name.length);
        return header;
    }

    @Test
    void readsTheLengthOfANameFromBothBytes() {
        assertThat(OriginalStore.office(central(0, 0))).as("a name up to the last byte").contains(OriginalStore.XLSX);
        assertThat(OriginalStore.office(central(0, 1))).contains(OriginalStore.XLSX);
        assertThat(OriginalStore.office(central(1, 0))).as("the name is 271 bytes long, beyond the end").isEmpty();
        byte[] late = Arrays.copyOf(new byte[] {'P', 'K', 3, 4}, 40);
        late[30] = 'P';
        late[31] = 'K';
        late[32] = 1;
        late[33] = 2;
        assertThat(OriginalStore.office(late)).as("a header too close to the end").isEmpty();
    }

    @Test
    void tellsTheTypeOfTheShortestFileAndTheLargestOne() {
        List<String> problems = new ArrayList<>();
        assertThat(type("%PDF-".getBytes(StandardCharsets.US_ASCII), problems)).contains(OriginalStore.PDF);
        assertThat(type("%P".getBytes(StandardCharsets.US_ASCII), problems)).isEmpty();
        assertThat(type(new byte[DocumentService.MAX_BYTES], problems)).as("25 MB still fit").isEmpty();
        assertThat(problems).containsExactly(OriginalStore.WRONG_TYPE, OriginalStore.WRONG_TYPE);
    }

    private static int indexOf(byte[] content, byte[] part, int from) {
        int last = -1;
        for (int at = from; at <= content.length - part.length; at++) {
            if (Arrays.equals(content, at, at + part.length, part, 0, part.length)) {
                last = at;
            }
        }
        return last;
    }

    @Test
    void storesSealedFilesAndReadsThemBack() {
        InvoiceOriginal stored = store.store(5, "2026/07", "C:\\Rechnungen\\Juli.xlsx", PDF, OriginalStore.XLSX, NOW);
        assertThat(stored.filename()).isEqualTo("Juli.xlsx");
        assertThat(stored.seal()).isEqualTo(seal.invoiceOriginal(5, 1, Seal.sha256(PDF)));
        assertThat(stored.createdBy()).isEqualTo("system");
        InvoiceOriginal unnamed = store.store(5, "2026/07", null, PDF, OriginalStore.PDF, NOW);
        assertThat(unnamed.filename()).isEqualTo("Rechnung_2026-07.pdf");
        store.store(6, "2026-08", "", PDF, OriginalStore.XML, NOW);

        assertThat(store.list(5)).containsExactly(stored, unnamed);
        assertThat(store.list(5).getFirst().createdAt()).isEqualTo(NOW);
        assertThat(store.find(5, 2)).contains(unnamed);
        assertThat(store.find(5, 3)).as("a file of another invoice").isEmpty();
        assertThat(store.list(6).getFirst().filename()).isEqualTo("Rechnung_2026-08.xml");
        assertThat(store.problems(stored)).isEmpty();
        assertThat(store.verified(stored)).isSameAs(stored);
    }

    @Test
    void listsTheFilesWithoutTheirContent() {
        InvoiceOriginal stored = store.store(5, "2026/07", "Juli.xlsx", PDF, OriginalStore.XLSX, NOW);
        InvoiceOriginal unnamed = store.store(5, "2026/07", null, PDF, OriginalStore.PDF, NOW);
        store.store(6, "2026-08", "", PDF, OriginalStore.XML, NOW);

        assertThat(store.summaries(5)).containsExactly(
                new OriginalSummary(1, 5, "Juli.xlsx", OriginalStore.XLSX, Seal.sha256(PDF), PDF.length, NOW, "system"),
                new OriginalSummary(2, 5, "Rechnung_2026-07.pdf", OriginalStore.PDF, Seal.sha256(PDF), PDF.length, NOW,
                        "system"));
        assertThat(store.summaries(5)).extracting(OriginalSummary::id).containsExactly(stored.id(), unnamed.id());
        assertThat(store.summaries(7)).isEmpty();
        String columns = OriginalStore.SUMMARIES.substring(0, OriginalStore.SUMMARIES.indexOf(" FROM "));
        assertThat(columns).as("the list selects no bytes").doesNotContainIgnoringCase("content")
                .doesNotContain("*");
    }

    @Test
    void namesAChangedFileAndKeepsItOutOfTheDownload() {
        store.store(5, "2026-07", "Juli.pdf", PDF, OriginalStore.PDF, NOW);
        rows.get(1L).put("content", "%PDF-1.7 Anders".getBytes(StandardCharsets.US_ASCII));
        InvoiceOriginal changed = store.list(5).getFirst();
        assertThat(store.problems(changed)).containsExactly(OriginalStore.WRONG_SHA.formatted("Juli.pdf",
                Seal.sha256(PDF), Seal.sha256("%PDF-1.7 Anders".getBytes(StandardCharsets.US_ASCII))));
        assertThatThrownBy(() -> store.find(5, 1)).isInstanceOf(InvoiceException.class)
                .hasMessage(OriginalStore.DAMAGED.formatted("Juli.pdf"));

        rows.get(1L).put("content", PDF);
        rows.get(1L).put("invoice_id", 6L);
        assertThat(store.problems(store.list(6).getFirst()))
                .as("a file moved to another invoice").containsExactly(OriginalStore.WRONG_SEAL.formatted("Juli.pdf"));
    }

    @Test
    void keepsItsBytesToItself() {
        byte[] content = PDF.clone();
        InvoiceOriginal original = new InvoiceOriginal(1, 5, "a.pdf", OriginalStore.PDF, content, "abc", 3, "s", NOW,
                "elena");
        content[0] = 'X';
        original.content()[1] = 'X';
        assertThat(original.content()).isEqualTo(PDF);
        InvoiceOriginal same = new InvoiceOriginal(1, 5, "b.pdf", OriginalStore.PDF, new byte[0], "abc", 0, "t", NOW,
                "jan");
        assertThat(original).isEqualTo(same).hasSameHashCodeAs(same)
                .isNotEqualTo(new InvoiceOriginal(2, 5, "a.pdf", OriginalStore.PDF, PDF, "abc", 3, "s", NOW, "elena"))
                .isNotEqualTo(new InvoiceOriginal(1, 6, "a.pdf", OriginalStore.PDF, PDF, "abc", 3, "s", NOW, "elena"))
                .isNotEqualTo(new InvoiceOriginal(1, 5, "a.pdf", OriginalStore.PDF, PDF, "abd", 3, "s", NOW, "elena"))
                .isNotEqualTo("a.pdf").hasToString("InvoiceOriginal[a.pdf, 3 bytes, sha256=abc]");
    }
}
