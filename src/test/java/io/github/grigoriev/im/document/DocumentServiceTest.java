package io.github.grigoriev.im.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.Seal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class DocumentServiceTest {

    private static final Instant NOW = Instant.parse("2027-05-12T08:00:00Z");
    private static final byte[] PDF = "%PDF-1.7 Bescheid".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AuditService audit = mock(AuditService.class);
    private final Seal seal = new Seal("test-only-integrity-key-0123456789abcdef");
    private final DocumentService service = new DocumentService(jdbc, audit, seal, Clock.fixed(NOW, ZoneOffset.UTC));

    /** The table in memory: one map of columns per row, in the order of the IDs. */
    private final Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();

    /** A statement that keeps its parameters and runs against {@link #rows}. */
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
        when(id.list()).thenAnswer(call -> new ArrayList<>(rows.keySet()));
        when(spec.update()).thenAnswer(call -> sql.equals(DocumentService.INSERT) ? insert(params) : update(params));
        return spec;
    }

    private List<Object> select(String sql, Map<String, Object> params, RowMapper<Object> mapper) throws SQLException {
        List<Map<String, Object>> found = new ArrayList<>();
        if (sql.equals(DocumentService.ALL)) {
            List<Map<String, Object>> all = new ArrayList<>(rows.values());
            all.sort((a, b) -> {
                int byDate = ((LocalDate) b.get("document_date")).compareTo((LocalDate) a.get("document_date"));
                return byDate == 0 ? Long.compare((Long) b.get("id"), (Long) a.get("id")) : byDate;
            });
            found.addAll(all);
        } else if (rows.containsKey((Long) params.get("id"))) {
            found.add(rows.get((Long) params.get("id")));
        }
        List<Object> result = new ArrayList<>();
        for (Map<String, Object> row : found) {
            result.add(mapper.mapRow(resultSet(withReplacedBy(row)), 0));
        }
        return result;
    }

    private Map<String, Object> withReplacedBy(Map<String, Object> row) {
        Map<String, Object> copy = new HashMap<>(row);
        copy.put("replaced_by", rows.values().stream().filter(other -> row.get("id").equals(other.get("replaces_id")))
                .map(other -> other.get("id")).findFirst().orElse(null));
        return copy;
    }

    private static ResultSet resultSet(Map<String, Object> row) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(anyString())).thenAnswer(call -> (Long) row.get(call.getArgument(0)));
        when(rs.getInt(anyString())).thenAnswer(call -> (Integer) row.get(call.getArgument(0)));
        when(rs.getString(anyString())).thenAnswer(call -> (String) row.get(call.getArgument(0)));
        when(rs.getObject(anyString(), any(Class.class))).thenAnswer(call -> row.get(call.getArgument(0)));
        when(rs.getTimestamp(anyString())).thenAnswer(call -> (Timestamp) row.get(call.getArgument(0)));
        List<String> file = List.of("filename", "media_type", "content", "sha256", "seal");
        when(rs.getString(anyInt())).thenAnswer(call -> (String) row.get(file.get((int) call.getArgument(0) - 1)));
        when(rs.getBytes(anyInt())).thenAnswer(call -> (byte[]) row.get(file.get((int) call.getArgument(0) - 1)));
        return rs;
    }

    private int insert(Map<String, Object> params) {
        long id = (Long) params.get("id");
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("category", params.get("category"));
        row.put("title", params.get("title"));
        row.put("document_date", params.get("documentDate"));
        row.put("year", params.get("year"));
        row.put("note", params.get("note"));
        row.put("replaces_id", params.get("replacesId"));
        row.put("filename", params.get("filename"));
        row.put("media_type", params.get("mediaType"));
        row.put("content", params.get("content"));
        row.put("sha256", params.get("sha256"));
        row.put("size", params.get("size"));
        row.put("created_at", params.get("createdAt"));
        row.put("created_by", params.get("createdBy"));
        row.put("seal", params.get("seal"));
        rows.put(id, row);
        return 1;
    }

    private int update(Map<String, Object> params) {
        Map<String, Object> row = rows.get((Long) params.get("id"));
        row.put("category", params.get("category"));
        row.put("title", params.get("title"));
        row.put("document_date", params.get("documentDate"));
        row.put("year", params.get("year"));
        row.put("note", params.get("note"));
        return 1;
    }

    @BeforeEach
    void table() {
        when(jdbc.sql(anyString())).thenAnswer(call -> statement(call.getArgument(0)));
    }

    private static DocumentForm form(DocumentCategory category, String title, LocalDate date, Integer year,
            String note) {
        DocumentForm form = new DocumentForm();
        form.setCategory(category);
        form.setTitle(title);
        form.setDocumentDate(date);
        form.setYear(year);
        form.setNote(note);
        return form;
    }

    private static DocumentForm assessment(LocalDate date) {
        return form(DocumentCategory.TAX_ASSESSMENT, " Einkommensteuerbescheid 2026 ", date, 2026, null);
    }

    @Test
    void storesADocumentWithItsTypeHashAndAuthor() {
        BusinessDocument added = service.add(assessment(LocalDate.of(2027, 5, 10)), "C:\\Scans\\Bescheid.pdf", PDF);
        assertThat(added.id()).isEqualTo(1);
        assertThat(added.title()).isEqualTo("Einkommensteuerbescheid 2026");
        assertThat(added.category()).isEqualTo(DocumentCategory.TAX_ASSESSMENT);
        assertThat(added.documentDate()).isEqualTo(LocalDate.of(2027, 5, 10));
        assertThat(added.year()).isEqualTo(2026);
        assertThat(added.note()).as("no note is an empty one").isEmpty();
        assertThat(added.filename()).isEqualTo("Bescheid.pdf");
        assertThat(added.mediaType()).isEqualTo("application/pdf");
        assertThat(added.sha256()).isEqualTo(Seal.sha256(PDF)).hasSize(64);
        assertThat(added.size()).isEqualTo(PDF.length);
        assertThat(added.createdAt()).isEqualTo(NOW);
        assertThat(added.createdBy()).isEqualTo("system");
        assertThat(added.replacesId()).isNull();
        assertThat(added.current()).isTrue();
        assertThat(service.file(1).orElseThrow()).isEqualTo(new DocumentFile("Bescheid.pdf", "application/pdf", PDF));
        assertThat(rows.get(1L).get("seal")).isEqualTo(seal.businessDocument(1, Seal.sha256(PDF)));
        assertThat(service.problems(1)).isEmpty();
        assertThat(service.damaged()).isEmpty();
        verify(audit).add(AuditAction.DOCUMENT_ADDED, "Steuerbescheid: Einkommensteuerbescheid 2026 (Bescheid.pdf, "
                + Seal.sha256(PDF) + ")");
    }

    @Test
    void knowsScansAsJpegAndPng() {
        DocumentForm form = form(DocumentCategory.CERTIFICATE, "Bescheinigung", LocalDate.of(2027, 1, 4), null,
                "  vom Kultusministerium ");
        assertThat(service.add(form, "scan.jpg", JPEG).mediaType()).isEqualTo("image/jpeg");
        assertThat(service.add(form, null, PNG)).satisfies(png -> {
            assertThat(png.mediaType()).isEqualTo("image/png");
            assertThat(png.filename()).isEqualTo("Dokument.png");
            assertThat(png.note()).isEqualTo("vom Kultusministerium");
        });
    }

    @Test
    void refusesAMissingEmptyBigOrUnknownFile() {
        DocumentForm form = assessment(LocalDate.of(2027, 5, 10));
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.add(form, "a.pdf", new byte[0]))
                .problems()).containsExactly("Bitte eine Datei wählen.");
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.add(form, "a.pdf", null))
                .problems()).containsExactly("Bitte eine Datei wählen.");
        byte[] big = new byte[DocumentService.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.add(form, "a.pdf", big))
                .problems()).containsExactly("Die Datei ist größer als 25 MB.");
        assertThat(catchThrowableOfType(DocumentException.class,
                () -> service.add(form, "a.txt", "Hallo".getBytes(StandardCharsets.UTF_8))).problems())
                .containsExactly("Bitte eine PDF-, JPG- oder PNG-Datei wählen.");
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.add(form, "a.pdf", new byte[] {'%'}))
                .problems()).as("shorter than any signature").containsExactly("Bitte eine PDF-, JPG- oder PNG-Datei wählen.");
        assertThat(rows).isEmpty();
        verify(audit, never()).add(any(AuditAction.class), anyString());
    }

    @Test
    void knowsAFileOfJustItsSignature() {
        DocumentForm form = assessment(LocalDate.of(2027, 5, 10));
        assertThat(service.add(form, "a.pdf", "%PDF-".getBytes(StandardCharsets.US_ASCII)).mediaType())
                .isEqualTo("application/pdf");
        assertThat(service.add(form, "a.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}).mediaType())
                .isEqualTo("image/jpeg");
        assertThat(service.add(form, "a.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
                .mediaType()).isEqualTo("image/png");
    }

    @Test
    void takesAFileOfTheLargestSize() {
        byte[] largest = new byte[DocumentService.MAX_BYTES];
        System.arraycopy(PDF, 0, largest, 0, PDF.length);
        assertThat(service.add(assessment(LocalDate.of(2027, 5, 10)), "a.pdf", largest).size())
                .isEqualTo(DocumentService.MAX_BYTES);
    }

    @Test
    void namesEveryProblemOfTheDescriptionAndOfTheFile() {
        DocumentForm form = form(null, " ", null, 1999, "x".repeat(2001));
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.add(form, "a.txt", new byte[] {1}))
                .problems()).containsExactly("Bitte eine PDF-, JPG- oder PNG-Datei wählen.", "Bitte die Art wählen.",
                "Bitte Titel angeben.", "Bitte das Datum angeben.", "Das Jahr liegt zwischen 2000 und 2100.",
                "Notiz hat höchstens 2000 Zeichen.");
        DocumentForm late = form(DocumentCategory.OTHER, "x".repeat(201), LocalDate.of(2027, 1, 1), 2101, null);
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.add(late, "a.pdf", PDF)).problems())
                .containsExactly("Titel hat höchstens 200 Zeichen.", "Das Jahr liegt zwischen 2000 und 2100.");
    }

    @Test
    void takesTheFirstAndTheLastYear() {
        assertThat(service.add(form(DocumentCategory.OTHER, "A", LocalDate.of(2027, 1, 1), 2000, null), "a.pdf", PDF)
                .year()).isEqualTo(2000);
        assertThat(service.add(form(DocumentCategory.OTHER, "B", LocalDate.of(2027, 1, 1), 2100, null), "b.pdf", PDF)
                .year()).isEqualTo(2100);
    }

    @Test
    void aNewVersionReplacesTheOldOneWhichStays() {
        service.add(assessment(LocalDate.of(2027, 5, 10)), "Bescheid.pdf", PDF);
        BusinessDocument second = service.replace(1, assessment(LocalDate.of(2027, 8, 1)), "Geändert.pdf", PNG);
        assertThat(second.id()).isEqualTo(2);
        assertThat(second.replacesId()).isEqualTo(1);
        verify(audit).add(eq(AuditAction.DOCUMENT_REPLACED), anyString());
        BusinessDocument third = service.replace(2, assessment(LocalDate.of(2027, 9, 1)), "Dritte.pdf", PDF);
        assertThat(service.find(1).orElseThrow().replacedBy()).isEqualTo(2);
        assertThat(service.find(1).orElseThrow().current()).isFalse();
        assertThat(service.list()).as("only the current version").containsExactly(third);
        assertThat(service.versions(1)).extracting(BusinessDocument::id).containsExactly(3L, 2L, 1L);
        assertThat(service.versions(2)).extracting(BusinessDocument::id).containsExactly(3L, 2L, 1L);
        assertThat(service.versions(3)).extracting(BusinessDocument::id).containsExactly(3L, 2L, 1L);
        assertThat(service.all()).hasSize(3);
        assertThat(service.file(1).orElseThrow().filename()).as("the old file stays").isEqualTo("Bescheid.pdf");
    }

    @Test
    void replacesOnlyTheCurrentVersionOfAnExistingDocument() {
        service.add(assessment(LocalDate.of(2027, 5, 10)), "Bescheid.pdf", PDF);
        service.replace(1, assessment(LocalDate.of(2027, 8, 1)), "Geändert.pdf", PDF);
        DocumentForm form = assessment(LocalDate.of(2027, 9, 1));
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.replace(1, form, "x.pdf", PDF))
                .problems()).containsExactly("Eine neuere Fassung ersetzt dieses Dokument. Bitte die aktuelle Fassung "
                + "bearbeiten.");
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.replace(9, form, "x.pdf", PDF))
                .problems()).containsExactly("Dieses Dokument gibt es nicht.");
        assertThatThrownBy(() -> service.change(1, form)).isInstanceOf(DocumentException.class);
        assertThat(rows).hasSize(2);
    }

    @Test
    void changesTheDescriptionAndKeepsTheFile() {
        service.add(assessment(LocalDate.of(2027, 5, 10)), "Bescheid.pdf", PDF);
        BusinessDocument changed = service.change(1, form(DocumentCategory.TAX_OFFICE, "Schreiben", LocalDate.of(2027,
                5, 11), null, "Steuernummer"));
        assertThat(changed.category()).isEqualTo(DocumentCategory.TAX_OFFICE);
        assertThat(changed.title()).isEqualTo("Schreiben");
        assertThat(changed.documentDate()).isEqualTo(LocalDate.of(2027, 5, 11));
        assertThat(changed.year()).isNull();
        assertThat(changed.note()).isEqualTo("Steuernummer");
        assertThat(changed.sha256()).isEqualTo(Seal.sha256(PDF));
        verify(audit).add(eq(AuditAction.DOCUMENT_CHANGED), anyString());
        DocumentForm blank = form(DocumentCategory.OTHER, "", LocalDate.of(2027, 1, 1), null, null);
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.change(1, blank)).problems())
                .containsExactly("Bitte Titel angeben.");
        assertThat(service.find(1).orElseThrow().title()).isEqualTo("Schreiben");
    }

    @Test
    void theArchiveOfAYearTakesTheYearItConcernsElseItsDate() {
        service.add(form(DocumentCategory.TAX_ASSESSMENT, "Bescheid 2026", LocalDate.of(2027, 5, 10), 2026, null),
                "a.pdf", PDF);
        service.add(form(DocumentCategory.CONTRACT, "Vertrag", LocalDate.of(2026, 9, 1), null, null), "b.pdf", PDF);
        service.add(form(DocumentCategory.OTHER, "Früher", LocalDate.of(2026, 2, 1), null, null), "c.pdf", PDF);
        service.add(form(DocumentCategory.OTHER, "Später", LocalDate.of(2027, 2, 1), null, null), "d.pdf", PDF);
        service.replace(3, form(DocumentCategory.OTHER, "Früher", LocalDate.of(2026, 2, 1), null, null), "e.pdf", PDF);
        assertThat(service.ofYear(2026)).extracting(BusinessDocument::title)
                .as("oldest first, the same date by ID, replaced versions too")
                .containsExactly("Früher", "Früher", "Vertrag", "Bescheid 2026");
        assertThat(service.ofYear(2026)).extracting(BusinessDocument::id).containsExactly(3L, 5L, 2L, 1L);
        assertThat(service.ofYear(2027)).extracting(BusinessDocument::title).containsExactly("Später");
        assertThat(service.ofYear(2025)).isEmpty();
        assertThat(service.years()).containsExactlyInAnyOrder(2026, 2027);
    }

    @Test
    void anUnknownDocumentHasNoFileAndNoVersions() {
        assertThat(service.find(4)).isEmpty();
        assertThat(service.file(4)).isEmpty();
        assertThat(service.versions(4)).isEmpty();
        assertThat(service.list()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
        "Bescheid.pdf|application/pdf|Bescheid.pdf",
        "/home/anna/Bescheid.pdf|application/pdf|Bescheid.pdf",
        "C:\\Scans\\Brief.jpg|image/jpeg|Brief.jpg",
        "a/b\\c.png|image/png|c.png",
        "' Brief\tneu.pdf '|application/pdf|Briefneu.pdf",
        "NULL|application/pdf|Dokument.pdf",
        "''|image/jpeg|Dokument.jpg",
        "' '|image/png|Dokument.png",
        ".|application/pdf|Dokument.pdf",
        "..|application/pdf|Dokument.pdf",
        "folder/|application/pdf|Dokument.pdf"})
    void keepsASafeFileName(String uploaded, String mediaType, String expected) {
        assertThat(DocumentService.filename(uploaded, mediaType)).isEqualTo(expected);
    }

    @Test
    void cutsALongFileNameAndKeepsAShortExtension() {
        String keep = DocumentService.filename("a".repeat(300) + ".pdf", "application/pdf");
        assertThat(keep).hasSize(200).endsWith("a.pdf");
        String none = DocumentService.filename("a".repeat(300), "application/pdf");
        assertThat(none).isEqualTo("a".repeat(200));
        String longExtension = DocumentService.filename("a".repeat(250) + "." + "b".repeat(20), "application/pdf");
        assertThat(longExtension).hasSize(200).doesNotContain(".");
        String tenCharacters = DocumentService.filename("a".repeat(250) + ".abcdefghi", "application/pdf");
        assertThat(tenCharacters).hasSize(200).endsWith(".abcdefghi");
        String eleven = DocumentService.filename("a".repeat(250) + ".abcdefghij", "application/pdf");
        assertThat(eleven).hasSize(200).doesNotContain(".");
        String hidden = DocumentService.filename("." + "a".repeat(250), "application/pdf");
        assertThat(hidden).hasSize(200).startsWith(".a");
        assertThat(DocumentService.filename("a".repeat(200), "application/pdf")).hasSize(200);
        assertThat(DocumentService.filename(".abc", "application/pdf")).isEqualTo(".abc");
        assertThat(DocumentService.filename("Brief.abcdefghi", "application/pdf")).isEqualTo("Brief.abcdefghi");
    }

    @Test
    void findsAFileChangedInTheDatabaseAndRefusesToHandItOut() {
        service.add(assessment(LocalDate.of(2027, 5, 10)), "Bescheid.pdf", PDF);
        service.add(assessment(LocalDate.of(2027, 5, 11)), "Zweiter.pdf", PDF);
        String sha = Seal.sha256(PDF);
        rows.get(1L).put("content", JPEG);
        assertThat(service.problems(1)).as("only the bytes changed: the hash says so, the seal still fits the hash")
                .containsExactly("Bescheid.pdf: Der Inhalt passt nicht zur gespeicherten Prüfsumme. Gespeichert: " + sha
                        + ", berechnet: " + Seal.sha256(JPEG) + ".");
        assertThatThrownBy(() -> service.file(1)).isInstanceOf(DocumentException.class)
                .hasMessage("Die Datei Bescheid.pdf ist beschädigt oder wurde außerhalb der App geändert. Die Seite "
                        + "des Dokuments zeigt, was abweicht.");
        rows.get(1L).put("sha256", Seal.sha256(JPEG));
        assertThat(service.problems(1)).as("bytes and hash changed together: the seal tells")
                .containsExactly("Bescheid.pdf: Das Siegel passt nicht. Prüfsumme oder Zuordnung der Datei wurden "
                        + "außerhalb der App geändert.");
        rows.get(2L).put("seal", rows.get(1L).get("seal"));
        assertThat(service.damaged()).extracting(Damage::what, Damage::href, Damage::invoiceId).containsExactly(
                tuple("Dokument Bescheid.pdf", "/documents/1", null), tuple("Dokument Zweiter.pdf", "/documents/2", null));
        assertThat(service.problems(9)).isEmpty();
        assertThat(service.file(9)).isEmpty();
    }

    @Test
    void theArchiveYearIsTheYearItConcernsElseTheYearOfItsDate() {
        BusinessDocument withYear = new BusinessDocument(1, DocumentCategory.OTHER, "A", LocalDate.of(2027, 1, 1), 2026,
                "", null, null, "a.pdf", "application/pdf", "h", 1, NOW, "anna");
        BusinessDocument without = new BusinessDocument(2, DocumentCategory.OTHER, "A", LocalDate.of(2027, 1, 1), null,
                "", null, 3L, "a.pdf", "application/pdf", "h", 1, NOW, "anna");
        assertThat(withYear.archiveYear()).isEqualTo(2026);
        assertThat(without.archiveYear()).isEqualTo(2027);
        assertThat(withYear.current()).isTrue();
        assertThat(without.current()).isFalse();
    }

    @Test
    void aFileKeepsItsBytes() {
        byte[] bytes = {1, 2, 3};
        DocumentFile file = new DocumentFile("a.pdf", "application/pdf", bytes);
        bytes[0] = 9;
        assertThat(file.content()).containsExactly(1, 2, 3);
        file.content()[1] = 9;
        assertThat(file.content()).containsExactly(1, 2, 3);
        assertThat(file).isEqualTo(new DocumentFile("a.pdf", "application/pdf", new byte[] {1, 2, 3}))
                .hasSameHashCodeAs(new DocumentFile("a.pdf", "application/pdf", new byte[] {1, 2, 3}))
                .isNotEqualTo(new DocumentFile("b.pdf", "application/pdf", new byte[] {1, 2, 3}))
                .isNotEqualTo(new DocumentFile("a.pdf", "image/png", new byte[] {1, 2, 3}))
                .isNotEqualTo(new DocumentFile("a.pdf", "application/pdf", new byte[] {1, 2}))
                .isNotEqualTo("a.pdf");
        assertThat(file.hashCode()).isNotEqualTo(new DocumentFile("a.pdf", "application/pdf", new byte[] {3}).hashCode())
                .isNotEqualTo(new DocumentFile("a.pdf", "image/png", new byte[] {1, 2, 3}).hashCode())
                .isNotEqualTo(new DocumentFile("b.pdf", "application/pdf", new byte[] {1, 2, 3}).hashCode());
        assertThat(file).hasToString("DocumentFile[a.pdf, application/pdf, 3 bytes]");
    }

    @Test
    void theFormStartsEmptyOrWithAStoredDocument() {
        DocumentForm empty = DocumentForm.empty(LocalDate.of(2027, 3, 18));
        assertThat(empty.getCategory()).isEqualTo(DocumentCategory.OTHER);
        assertThat(empty.getDocumentDate()).isEqualTo(LocalDate.of(2027, 3, 18));
        assertThat(empty.getTitle()).isNull();
        assertThat(empty.getYear()).isNull();
        assertThat(empty.getNote()).isNull();
        BusinessDocument stored = new BusinessDocument(1, DocumentCategory.CONTRACT, "Vertrag", LocalDate.of(2027, 1, 2),
                2027, "Goethe", null, null, "a.pdf", "application/pdf", "h", 1, NOW, "anna");
        DocumentForm form = DocumentForm.of(stored);
        assertThat(form.getCategory()).isEqualTo(DocumentCategory.CONTRACT);
        assertThat(form.getTitle()).isEqualTo("Vertrag");
        assertThat(form.getDocumentDate()).isEqualTo(LocalDate.of(2027, 1, 2));
        assertThat(form.getYear()).isEqualTo(2027);
        assertThat(form.getNote()).isEqualTo("Goethe");
    }

    @Test
    void everyKindHasAGermanLabel() {
        assertThat(DocumentCategory.values()).extracting(DocumentCategory::label).containsExactly(
                "Verfahrensdokumentation", "Steuerbescheid", "Finanzamt", "Bescheinigung", "Vertrag", "Sonstiges");
    }

    @Test
    void anExceptionKeepsItsProblems() {
        DocumentException e = new DocumentException(List.of("A.", "B."));
        assertThat(e.problems()).containsExactly("A.", "B.");
        assertThat(e).hasMessage("A. B.");
        assertThat(Optional.of(e.problems())).isPresent();
    }
}
