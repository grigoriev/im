package io.github.grigoriev.im.archive;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.expense.Expense;
import io.github.grigoriev.im.expense.ExpenseService;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceIntegrity;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.OriginalStore;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Writes the archive of a year (docs/PLAN.md, section 7): a ZIP with the stored PDF and XML of every invoice issued
 * with an invoice date in that year, and the original files of every external one, byte for byte, and a table of
 * contents with their SHA-256. The folder
 * "Dokumente" holds every version of the business documents of that year, with a table of contents of its own.
 */
@Service
public class ArchiveService {

    /** The table of contents, for a spreadsheet: semicolons, UTF-8 with byte order mark. */
    static final String CONTENTS = "Inhalt.csv";
    static final String HEAD = "Rechnungsnummer;Rechnungsdatum;Kunde;Datei;Bytes;SHA-256";
    static final String DAMAGED = "Das Archiv bleibt aus, denn diese Dateien fehlen, sind beschädigt oder wurden "
            + "außerhalb der App geändert: %s.";

    /** The folder of the receipts and its table of contents. */
    static final String EXPENSE_FOLDER = "Ausgaben/";
    static final String EXPENSE_HEAD = "Zahlung;Betrag;Art;Empfänger;Beschreibung;Anlagegut;Storno;Datei;Bytes;SHA-256";

    /** The folder of the business documents and its table of contents. */
    static final String FOLDER = "Dokumente/";
    static final String DOCUMENT_HEAD = "Art;Titel;Datum;Jahr;Fassung;Datei;Bytes;SHA-256";

    /** Only the app may read the archive while it grows: it holds every invoice of the year. */
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY = PosixFilePermissions.asFileAttribute(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceService invoices;
    private final DocumentStore documents;
    private final OriginalStore originals;
    private final DocumentService businessDocuments;
    private final InvoiceIntegrity integrity;
    private final ExpenseService expenses;
    private final Path workFolder;

    /**
     * Creates the service.
     *
     * @param invoices the invoices
     * @param documents the stored documents and snapshots
     * @param originals the original files of external invoices
     * @param businessDocuments the business documents
     * @param integrity checks the invoices
     * @param expenses the expenses and their receipts
     */
    @Autowired
    public ArchiveService(InvoiceService invoices, DocumentStore documents, OriginalStore originals,
            DocumentService businessDocuments, InvoiceIntegrity integrity, ExpenseService expenses) {
        this(invoices, documents, originals, businessDocuments, integrity, expenses,
                Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * Creates the service with a folder of its own for the archive while it grows.
     *
     * @param invoices the invoices
     * @param documents the stored documents and snapshots
     * @param originals the original files of external invoices
     * @param businessDocuments the business documents
     * @param integrity checks the invoices
     * @param expenses the expenses and their receipts
     * @param workFolder where the archive grows before it goes out
     */
    ArchiveService(InvoiceService invoices, DocumentStore documents, OriginalStore originals,
            DocumentService businessDocuments, InvoiceIntegrity integrity, ExpenseService expenses, Path workFolder) {
        this.invoices = invoices;
        this.documents = documents;
        this.originals = originals;
        this.businessDocuments = businessDocuments;
        this.integrity = integrity;
        this.expenses = expenses;
        this.workFolder = workFolder;
    }

    /**
     * Checks what the archive of a year would hold, before it goes out.
     *
     * @param year the year of the invoice dates
     * @return the invoices and documents with a problem, empty if all is in order
     */
    public List<String> damaged(int year) {
        List<String> damaged = new ArrayList<>();
        for (Invoice invoice : issued(year)) {
            if (!integrity.check(invoice).intact()) {
                damaged.add("Rechnung " + invoice.number());
            }
        }
        for (BusinessDocument document : businessDocuments.ofYear(year)) {
            if (!businessDocuments.problems(document.id()).isEmpty()) {
                damaged.add("Dokument " + document.filename());
            }
        }
        for (Expense expense : expenses.ofYear(year)) {
            if (!expenses.problems(expense.id()).isEmpty()) {
                damaged.add("Beleg " + expense.filename());
            }
        }
        return damaged;
    }

    /**
     * Returns the years with business documents, so the year picker of the archive offers them too.
     *
     * @return the archive years of the documents
     */
    public Set<Integer> years() {
        return businessDocuments.years();
    }

    /**
     * Writes the archive of a year. It reads one invoice's documents at a time.
     *
     * @param year the year of the invoice dates
     * @param out where the ZIP goes; it is closed at the end
     * @throws UncheckedIOException if the output fails
     * @throws InvoiceException if a file of the year is missing, broken or changed; then nothing is written
     */
    public void write(int year, OutputStream out) {
        // Checked before the first entry: an entry cannot be taken back, a late failure would leave half an archive.
        List<String> damaged = damaged(year);
        if (!damaged.isEmpty()) {
            throw new InvoiceException(List.of(DAMAGED.formatted(String.join(", ", damaged))));
        }
        // The ZIP grows in a file of its own and goes out only once every file passed its check on the way in: a file
        // changed after the check above stops it before a single byte leaves.
        try {
            Path file = Files.createTempFile(workFolder, "im-archive-", ".zip", OWNER_ONLY);
            try {
                try (OutputStream zip = Files.newOutputStream(file)) {
                    zip(year, zip);
                }
                try (out) {
                    Files.copy(file, out);
                }
            } finally {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void zip(int year, OutputStream out) throws IOException {
        List<Invoice> issued = issued(year);
        StringBuilder contents = new StringBuilder().append((char) 0xFEFF).append(HEAD).append("\r\n");
        Set<String> names = new HashSet<>();
        // Each new entry and the final close end the entry before.
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Invoice invoice : issued) {
                InvoiceDocument document = documents.verifiedSnapshot(invoice);
                for (ArchivedFile file : files(invoice)) {
                    String name = unique(names, file.filename, invoice.id());
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(file.content);
                    contents.append(String.join(";", quoted(invoice.number()), GERMAN.format(invoice.issueDate()),
                            quoted(Customer.label(document.buyer().name(), document.buyer().contactName())), name, Integer.toString(file.size), file.sha256))
                            .append("\r\n");
                }
            }
            zip.putNextEntry(new ZipEntry(CONTENTS));
            zip.write(contents.toString().getBytes(StandardCharsets.UTF_8));
            writeDocuments(year, zip);
            writeExpenses(year, zip);
        }
    }

    /** A file of an invoice in the archive: a document of the app or an original file. */
    private static final class ArchivedFile {

        private final String filename;
        private final byte[] content;
        private final int size;
        private final String sha256;

        ArchivedFile(String filename, byte[] content, int size, String sha256) {
            this.filename = filename;
            this.content = content;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    /** The checked files of an invoice: its PDF and XML, or the original files of an external one. */
    private List<ArchivedFile> files(Invoice invoice) {
        if (invoice.format().isExternal()) {
            return originals.list(invoice.id()).stream().map(originals::verified)
                    .map(file -> new ArchivedFile(file.filename(), file.content(), file.size(), file.sha256())).toList();
        }
        return documents.list(invoice.id()).stream().map(documents::verified)
                .map(file -> new ArchivedFile(file.filename(), file.content(), file.size(), file.sha256())).toList();
    }

    private List<Invoice> issued(int year) {
        return invoices.list().stream()
                .filter(invoice -> !invoice.isDraft() && invoice.issueDate().getYear() == year)
                .sorted(Comparator.comparing(Invoice::issueDate).thenComparing(Invoice::id)).toList();
    }

    /** Writes the business documents of the year into their folder, one file at a time; none, no folder. */
    private void writeDocuments(int year, ZipOutputStream zip) throws IOException {
        List<BusinessDocument> ofYear = businessDocuments.ofYear(year);
        if (ofYear.isEmpty()) {
            return;
        }
        StringBuilder contents = new StringBuilder().append((char) 0xFEFF).append(DOCUMENT_HEAD).append("\r\n");
        Set<String> names = new HashSet<>();
        for (BusinessDocument document : ofYear) {
            String name = FOLDER + unique(names, document.filename(), document.id());
            zip.putNextEntry(new ZipEntry(name));
            zip.write(businessDocuments.file(document.id()).orElseThrow().content());
            contents.append(String.join(";", quoted(document.category().label()), quoted(document.title()),
                    GERMAN.format(document.documentDate()), document.year() == null ? "" : document.year().toString(),
                    document.current() ? "aktuell" : "ersetzt", quoted(name), Integer.toString(document.size()),
                    document.sha256())).append("\r\n");
        }
        zip.putNextEntry(new ZipEntry(FOLDER + CONTENTS));
        zip.write(contents.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Writes the receipts of the year into their folder, the canceled ones too, marked; none, no folder. */
    private void writeExpenses(int year, ZipOutputStream zip) throws IOException {
        List<Expense> ofYear = expenses.ofYear(year);
        if (ofYear.isEmpty()) {
            return;
        }
        StringBuilder contents = new StringBuilder().append((char) 0xFEFF).append(EXPENSE_HEAD).append("\r\n");
        Set<String> names = new HashSet<>();
        for (Expense expense : ofYear) {
            String name = EXPENSE_FOLDER + unique(names, expense.filename(), expense.id());
            zip.putNextEntry(new ZipEntry(name));
            zip.write(expenses.file(expense.id()).orElseThrow().content());
            contents.append(String.join(";", GERMAN.format(expense.paidOn()), amount(expense.amount()),
                    quoted(expense.category().label()), quoted(expense.vendor()), quoted(expense.description()),
                    expense.asset() ? "ja" : "nein", expense.cancelled() ? quoted("storniert: " + expense.cancelReason())
                    : "", quoted(name), Integer.toString(expense.size()), expense.sha256())).append("\r\n");
        }
        zip.putNextEntry(new ZipEntry(EXPENSE_FOLDER + CONTENTS));
        zip.write(contents.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String amount(BigDecimal amount) {
        return amount.toPlainString().replace('.', ',');
    }

    /**
     * Returns a file name not used in the ZIP yet. Two numbers can give the same name, for example "2027/11" and
     * "2027-11": the second gets the ID before its extension, and a counter if that name is taken too. A name
     * without an extension gets them at its end.
     */
    static String unique(Set<String> names, String filename, long id) {
        String name = filename;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        for (int n = 1; !names.add(name); n++) {
            name = base + "_" + id + (n == 1 ? "" : "_" + n) + extension;
        }
        return name;
    }

    /** A text field: quoted, with doubled quotes, and without a leading formula character. */
    static String quoted(String text) {
        String safe = !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
