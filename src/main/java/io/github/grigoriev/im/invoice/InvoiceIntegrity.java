package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.EInvoiceException;
import io.github.grigoriev.im.einvoice.EInvoiceFacts;
import io.github.grigoriev.im.einvoice.EInvoiceFacts.Difference;
import io.github.grigoriev.im.einvoice.EInvoiceWriter;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.IntegrityCheck;
import io.github.grigoriev.im.integrity.Seal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Checks an issued invoice: the seal of its snapshot, each document against its SHA-256 and seal, the XML of a
 * ZUGFeRD PDF against the XML file, and the facts of the XML against the snapshot. The last one names what changed.
 */
@Service
public class InvoiceIntegrity implements IntegrityCheck {

    static final String SEAL = "SELECT seal FROM invoice WHERE id = :id";
    static final String SNAPSHOT_CHANGED = "Der Stand der Ausstellung passt nicht zu seinem Siegel. Er wurde außerhalb "
            + "der App geändert.";
    static final String SNAPSHOT_UNREADABLE = "Der Stand der Ausstellung lässt sich nicht lesen.";
    static final String MISSING = "Die Datei %s fehlt.";
    static final String XML_DIFFERS = "Die XML-Datei weicht vom Stand der Ausstellung ab.";
    static final String XML_UNREADABLE = "Die XML-Datei lässt sich nicht lesen.";
    static final String PDF_XML_DIFFERS = "Die XML in der PDF-Datei weicht von der XML-Datei ab.";
    static final String PDF_WITHOUT_XML = "Die PDF-Datei enthält keine lesbare XML.";

    private final InvoiceService invoices;
    private final DocumentStore documents;
    private final EInvoiceWriter writer;
    private final JdbcClient jdbc;

    /**
     * The result of the check of one invoice.
     *
     * @param problems what is wrong, empty if all is in order
     * @param differences the facts of the XML that differ from the snapshot
     */
    public record Report(List<String> problems, List<Difference> differences) {

        /** Keeps copies of the lists. */
        public Report {
            problems = List.copyOf(problems);
            differences = List.copyOf(differences);
        }

        /**
         * Tells whether all is in order.
         *
         * @return whether the check found nothing
         */
        public boolean intact() {
            return problems.isEmpty();
        }
    }

    /**
     * Creates the check.
     *
     * @param invoices the invoices
     * @param documents the documents and snapshots
     * @param writer reads the XML of a ZUGFeRD PDF
     * @param jdbc reads the seal of a snapshot
     */
    public InvoiceIntegrity(InvoiceService invoices, DocumentStore documents, EInvoiceWriter writer, JdbcClient jdbc) {
        this.invoices = invoices;
        this.documents = documents;
        this.writer = writer;
        this.jdbc = jdbc;
    }

    /**
     * Checks an issued invoice.
     *
     * @param invoice the invoice
     * @return what the check found
     */
    public Report check(Invoice invoice) {
        List<String> problems = new ArrayList<>();
        String seal = jdbc.sql(SEAL).param("id", invoice.id()).query(rs -> rs.next() ? rs.getString(1) : null);
        if (!Seal.matches(seal, documents.seal(invoice.id(), invoice.number(), invoice.snapshot()))) {
            problems.add(SNAPSHOT_CHANGED);
        }
        Map<DocumentKind, StoredDocument> files = new EnumMap<>(DocumentKind.class);
        documents.list(invoice.id()).forEach(file -> files.put(file.kind(), file));
        for (DocumentKind kind : DocumentKind.values()) {
            StoredDocument file = files.get(kind);
            if (file == null) {
                problems.add(MISSING.formatted(kind.filename(invoice.number())));
            } else {
                problems.addAll(documents.problems(file));
            }
        }
        InvoiceDocument issued;
        try {
            issued = documents.restore(invoice.snapshot());
        } catch (RuntimeException _) {
            problems.add(SNAPSHOT_UNREADABLE);
            return new Report(problems, List.of());
        }
        StoredDocument xml = files.get(DocumentKind.XML);
        if (xml == null) {
            return new Report(problems, List.of());
        }
        List<Difference> differences = differences(problems, issued, xml);
        StoredDocument pdf = files.get(DocumentKind.PDF);
        if (pdf != null && issued.format().isPdf()) {
            compareEmbedded(problems, pdf, xml);
        }
        return new Report(problems, differences);
    }

    /**
     * Checks every issued invoice.
     *
     * @return the invoices with a problem
     */
    @Override
    public List<Damage> damaged() {
        List<Damage> damaged = new ArrayList<>();
        for (Invoice invoice : invoices.list()) {
            if (!invoice.isDraft()) {
                Report report = check(invoice);
                if (!report.intact()) {
                    damaged.add(new Damage(invoice.id(), "Rechnung " + invoice.number(), "/invoices/" + invoice.id(),
                            report.problems()));
                }
            }
        }
        return damaged;
    }

    private static List<Difference> differences(List<String> problems, InvoiceDocument issued, StoredDocument xml) {
        try {
            List<Difference> differences = EInvoiceFacts.of(issued).differences(EInvoiceFacts.read(xml.content()));
            if (!differences.isEmpty()) {
                problems.add(XML_DIFFERS);
            }
            return differences;
        } catch (EInvoiceException _) {
            problems.add(XML_UNREADABLE);
            return List.of();
        }
    }

    private void compareEmbedded(List<String> problems, StoredDocument pdf, StoredDocument xml) {
        try {
            if (!Arrays.equals(writer.embeddedXml(pdf.content()), xml.content())) {
                problems.add(PDF_XML_DIFFERS);
            }
        } catch (RuntimeException _) {
            problems.add(PDF_WITHOUT_XML);
        }
    }
}
