package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentException;
import io.github.grigoriev.im.document.DocumentForm;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.document.ProcedureForm;
import io.github.grigoriev.im.document.ProcedureService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The business documents against PostgreSQL: versions, and a table that keeps every file. */
@SpringBootTest
@Import(TestDatabase.class)
class DocumentIT {

    private static final byte[] PDF = "%PDF-1.7 Bescheid".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private DocumentService documents;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcedureService procedure;

    @Autowired
    private SellerService sellers;

    private static DocumentForm form(String title, LocalDate date) {
        DocumentForm form = new DocumentForm();
        form.setCategory(DocumentCategory.TAX_ASSESSMENT);
        form.setTitle(title);
        form.setDocumentDate(date);
        form.setYear(2026);
        return form;
    }

    @Test
    void keepsEveryVersionWithItsFile() {
        String title = "Einkommensteuerbescheid " + System.nanoTime();
        BusinessDocument first = documents.add(form(title, LocalDate.of(2027, 5, 10)), "Bescheid.pdf", PDF);
        BusinessDocument second = documents.replace(first.id(), form(title, LocalDate.of(2027, 8, 1)), "Neu.pdf", PDF);
        assertThat(documents.find(first.id()).orElseThrow().replacedBy()).isEqualTo(second.id());
        assertThat(documents.list()).extracting(BusinessDocument::id).contains(second.id())
                .doesNotContain(first.id());
        assertThat(documents.versions(first.id())).extracting(BusinessDocument::id)
                .containsExactly(second.id(), first.id());
        assertThat(documents.file(first.id()).orElseThrow().content()).isEqualTo(PDF);
        assertThat(documents.ofYear(2026)).extracting(BusinessDocument::id).contains(first.id(), second.id());
        assertThat(documents.years()).contains(2026);
        assertThat(documents.change(second.id(), form(title + " geändert", LocalDate.of(2027, 8, 2))).title())
                .endsWith(" geändert");
        DocumentForm late = form(title, LocalDate.of(2027, 9, 1));
        long firstId = first.id();
        assertThatThrownBy(() -> documents.replace(firstId, late, "x.pdf", PDF)).isInstanceOf(DocumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action LIKE 'DOCUMENT_%' AND detail LIKE ?",
                Integer.class, "%" + title + "%")).isEqualTo(3);
    }

    @Test
    void theDatabaseKeepsTheFileAndLetsOnlyTheDescriptionChange() {
        BusinessDocument document = documents.add(form("Bescheid " + System.nanoTime(), LocalDate.of(2027, 5, 10)),
                "Bescheid.pdf", PDF);
        long id = document.id();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM business_document WHERE id = ?", id))
                .hasMessageContaining("is kept");
        assertThatThrownBy(() -> jdbc.update("UPDATE business_document SET content = ? WHERE id = ?", new byte[] {1}, id))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE business_document SET filename = 'x.pdf' WHERE id = ?", id))
                .hasMessageContaining("immutable");
        assertThat(jdbc.update("UPDATE business_document SET note = 'Notiz' WHERE id = ?", id)).isEqualTo(1);
        documents.replace(id, form("Bescheid", LocalDate.of(2027, 6, 1)), "Neu.pdf", PDF);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO business_document (category, title, document_date, note, replaces_id, filename, media_type,
                    content, sha256, size, created_at, created_by)
                VALUES ('OTHER', 'Zweite', DATE '2027-06-02', '', ?, 'z.pdf', 'application/pdf', ?, ?, 1, now(), 'x')""",
                id, PDF, "0".repeat(64))).as("one document has one newer version").hasMessageContaining("replaces_id");
    }

    @Test
    void writesTheVerfahrensdokumentationAsVersions() throws IOException {
        SellerTestData.save(sellers, SellerTestData.profile());
        ProcedureForm form = new ProcedureForm();
        form.setValidFrom(LocalDate.of(2027, 1, 1));
        form.setExpenses("WISO Steuern");
        form.setArchive("in einem Dropbox-Ordner");
        form.setChange("Erstfassung " + System.nanoTime());
        BusinessDocument first = procedure.save(form);
        assertThat(first.filename()).startsWith("Verfahrensdokumentation_").endsWith(".pdf");
        assertThat(procedure.form().getExpenses()).as("the values stay for the next version").isEqualTo("WISO Steuern");
        form.setChange("Neue Version der App");
        BusinessDocument second = procedure.save(form);
        assertThat(second.replacesId()).as("a new version of the current one").isNotNull();
        assertThat(procedure.current()).contains(documents.find(second.id()).orElseThrow());
        try (PDDocument pdf = Loader.loadPDF(documents.file(second.id()).orElseThrow().content())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Erika Mustermann").contains("12/345/67890").contains("WISO Steuern")
                    .contains("Neue Version der App").contains(first.note());
        }
    }
}
