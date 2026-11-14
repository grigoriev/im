package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/**
 * Writes the archive of a year (docs/PLAN.md, section 7): a ZIP with the stored PDF and XML of every invoice issued
 * with an invoice date in that year, byte for byte, and a table of contents with their SHA-256.
 */
@Service
public class ArchiveService {

    /** The table of contents, for a spreadsheet: semicolons, UTF-8 with byte order mark. */
    static final String CONTENTS = "Inhalt.csv";
    static final String HEAD = "Rechnungsnummer;Rechnungsdatum;Kunde;Datei;Bytes;SHA-256";

    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceService invoices;
    private final DocumentStore documents;

    /**
     * Creates the service.
     *
     * @param invoices the invoices
     * @param documents the stored documents and snapshots
     */
    public ArchiveService(InvoiceService invoices, DocumentStore documents) {
        this.invoices = invoices;
        this.documents = documents;
    }

    /**
     * Writes the archive of a year. It reads one invoice's documents at a time.
     *
     * @param year the year of the invoice dates
     * @param out where the ZIP goes; it is closed at the end
     * @throws UncheckedIOException if the output fails
     */
    public void write(int year, OutputStream out) {
        List<Invoice> issued = invoices.list().stream()
                .filter(invoice -> !invoice.isDraft() && invoice.issueDate().getYear() == year)
                .sorted(Comparator.comparing(Invoice::issueDate).thenComparing(Invoice::id)).toList();
        StringBuilder contents = new StringBuilder().append((char) 0xFEFF).append(HEAD).append("\r\n");
        Set<String> names = new HashSet<>();
        // Each new entry and the final close end the entry before.
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Invoice invoice : issued) {
                InvoiceDocument document = documents.restore(invoice.snapshot());
                for (StoredDocument file : documents.list(invoice.id())) {
                    String name = unique(names, file.filename(), invoice.id());
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(file.content());
                    contents.append(String.join(";", quoted(invoice.number()), GERMAN.format(invoice.issueDate()),
                            quoted(document.buyer().name()), name, Integer.toString(file.size()), file.sha256()))
                            .append("\r\n");
                }
            }
            zip.putNextEntry(new ZipEntry(CONTENTS));
            zip.write(contents.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns a file name not used in the ZIP yet. Two numbers can give the same name, for example "2027/11" and
     * "2027-11": the second gets the invoice ID before its extension.
     */
    static String unique(Set<String> names, String filename, long invoiceId) {
        String name = filename;
        if (!names.add(name)) {
            int dot = filename.lastIndexOf('.');
            name = filename.substring(0, dot) + "_" + invoiceId + filename.substring(dot);
            names.add(name);
        }
        return name;
    }

    /** A text field: quoted, with doubled quotes, and without a leading formula character. */
    static String quoted(String text) {
        String safe = !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
