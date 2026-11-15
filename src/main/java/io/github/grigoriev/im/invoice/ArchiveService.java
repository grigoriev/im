package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
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
 * with an invoice date in that year, byte for byte, and a table of contents with their SHA-256. The folder
 * "Dokumente" holds every version of the business documents of that year, with a table of contents of its own.
 */
@Service
public class ArchiveService {

    /** The table of contents, for a spreadsheet: semicolons, UTF-8 with byte order mark. */
    static final String CONTENTS = "Inhalt.csv";
    static final String HEAD = "Rechnungsnummer;Rechnungsdatum;Kunde;Datei;Bytes;SHA-256";
    static final String DAMAGED = "Das Archiv bleibt aus, denn diese Dateien fehlen, sind beschädigt oder wurden "
            + "außerhalb der App geändert: %s.";

    /** The folder of the business documents and its table of contents. */
    static final String FOLDER = "Dokumente/";
    static final String DOCUMENT_HEAD = "Art;Titel;Datum;Jahr;Fassung;Datei;Bytes;SHA-256";

    /** Only the app may read the archive while it grows: it holds every invoice of the year. */
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY = PosixFilePermissions.asFileAttribute(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceService invoices;
    private final DocumentStore documents;
    private final DocumentService businessDocuments;
    private final InvoiceIntegrity integrity;
    private final Path workFolder;

    /**
     * Creates the service.
     *
     * @param invoices the invoices
     * @param documents the stored documents and snapshots
     * @param businessDocuments the business documents
     * @param integrity checks the invoices
     */
    @Autowired
    public ArchiveService(InvoiceService invoices, DocumentStore documents, DocumentService businessDocuments,
            InvoiceIntegrity integrity) {
        this(invoices, documents, businessDocuments, integrity, Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * Creates the service with a folder of its own for the archive while it grows.
     *
     * @param invoices the invoices
     * @param documents the stored documents and snapshots
     * @param businessDocuments the business documents
     * @param integrity checks the invoices
     * @param workFolder where the archive grows before it goes out
     */
    ArchiveService(InvoiceService invoices, DocumentStore documents, DocumentService businessDocuments,
            InvoiceIntegrity integrity, Path workFolder) {
        this.invoices = invoices;
        this.documents = documents;
        this.businessDocuments = businessDocuments;
        this.integrity = integrity;
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
                for (StoredDocument file : documents.list(invoice.id()).stream().map(documents::verified).toList()) {
                    String name = unique(names, file.filename(), invoice.id());
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(file.content());
                    contents.append(String.join(";", quoted(invoice.number()), GERMAN.format(invoice.issueDate()),
                            quoted(Customer.label(document.buyer().name(), document.buyer().contactName())), name, Integer.toString(file.size()), file.sha256()))
                            .append("\r\n");
                }
            }
            zip.putNextEntry(new ZipEntry(CONTENTS));
            zip.write(contents.toString().getBytes(StandardCharsets.UTF_8));
            writeDocuments(year, zip);
        }
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
