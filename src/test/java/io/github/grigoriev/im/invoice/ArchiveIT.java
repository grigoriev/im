package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.TestDatabase;
import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentForm;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.expense.Expense;
import io.github.grigoriev.im.expense.ExpenseCategory;
import io.github.grigoriev.im.expense.ExpenseForm;
import io.github.grigoriev.im.expense.ExpenseService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The archive of a year against PostgreSQL: it holds the business documents and receipts byte for byte, and a receipt
 * changed in the database stops it before a single byte goes out. Each test uses its own year.
 */
@SpringBootTest
@Import(TestDatabase.class)
class ArchiveIT {

    private static final byte[] RECEIPT = "%PDF-1.4 Quittung".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LETTER = "%PDF-1.4 Brief".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private DocumentStore documents;

    @Autowired
    private DocumentService businessDocuments;

    @Autowired
    private InvoiceIntegrity integrity;

    @Autowired
    private ExpenseService expenses;

    @Autowired
    private TestDatabase.Owner owner;

    @TempDir
    private Path workFolder;

    private ArchiveService archive() {
        return new ArchiveService(invoices, documents, businessDocuments, integrity, expenses, workFolder);
    }

    private BusinessDocument letter(LocalDate date) {
        DocumentForm form = new DocumentForm();
        form.setCategory(DocumentCategory.TAX_OFFICE);
        form.setTitle("Steuernummer");
        form.setDocumentDate(date);
        return businessDocuments.add(form, "Brief.pdf", LETTER);
    }

    private Expense receipt(LocalDate paidOn) {
        ExpenseForm form = new ExpenseForm();
        form.setPaidOn(paidOn);
        form.setAmount("49,90");
        form.setCategory(ExpenseCategory.LITERATURE);
        form.setVendor("Buchhandlung");
        form.setDescription("Wörterbuch");
        return expenses.add(form, "Quittung.pdf", RECEIPT);
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    @Test
    void holdsTheDocumentsAndReceiptsOfTheYearByteForByte() throws IOException {
        letter(LocalDate.of(2011, 3, 1));
        Expense receipt = receipt(LocalDate.of(2011, 4, 1));
        receipt(LocalDate.of(2010, 12, 31));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        archive().write(2011, out);

        Map<String, byte[]> entries = unzip(out.toByteArray());
        assertThat(entries).containsEntry("Dokumente/Brief.pdf", LETTER).containsEntry("Ausgaben/Quittung.pdf", RECEIPT)
                .containsKeys("Inhalt.csv", "Dokumente/Inhalt.csv", "Ausgaben/Inhalt.csv")
                .as("the receipt of the year before stays out").hasSize(5);
        assertThat(new String(entries.get("Ausgaben/Inhalt.csv"), StandardCharsets.UTF_8))
                .contains("01.04.2011;49,90;\"Fachliteratur\"").contains(receipt.sha256());
        assertThat(workFolder).as("the archive left no file behind").isEmptyDirectory();
    }

    @Test
    void aReceiptChangedInTheDatabaseStopsTheArchiveBeforeAByte() throws IOException {
        Expense receipt = receipt(LocalDate.of(2012, 4, 1));
        letter(LocalDate.of(2012, 3, 1));
        JdbcTemplate asOwner = new JdbcTemplate(owner.dataSource(null));
        byte[] changed = "%PDF-1.4 falsch".getBytes(StandardCharsets.US_ASCII);
        String update = "UPDATE expense SET content = ?, sha256 = encode(sha256(?), 'hex') WHERE id = ?";
        asOwner.execute("ALTER TABLE expense DISABLE TRIGGER expense_frozen");
        try {
            asOwner.update(update, changed, changed, receipt.id());
        } finally {
            asOwner.execute("ALTER TABLE expense ENABLE TRIGGER expense_frozen");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ArchiveService archive = archive();
            assertThat(archive.damaged(2012)).containsExactly("Beleg Quittung.pdf");
            assertThatThrownBy(() -> archive.write(2012, out)).isInstanceOf(InvoiceException.class)
                    .hasMessageContaining("Beleg Quittung.pdf");
            assertThat(out.size()).as("nothing went out").isZero();
            assertThat(workFolder).as("and nothing stayed behind").isEmptyDirectory();
        } finally {
            asOwner.execute("ALTER TABLE expense DISABLE TRIGGER expense_frozen");
            asOwner.update(update, RECEIPT, RECEIPT, receipt.id());
            asOwner.execute("ALTER TABLE expense ENABLE TRIGGER expense_frozen");
        }
        assertThat(archive().damaged(2012)).isEmpty();
    }
}
