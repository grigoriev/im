package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.IssuedEInvoice;
import io.github.grigoriev.im.integrity.Seal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps what the issue freezes: the documents with their SHA-256 and seal, and the snapshot of the invoice as JSON. A
 * document leaves the store only if it still matches both.
 */
@Component
public class DocumentStore {

    static final String DAMAGED = "Die Datei %s ist beschädigt oder wurde außerhalb der App geändert. Die Seite der "
            + "Rechnung zeigt, was abweicht.";
    static final String WRONG_SHA = "%s: Der Inhalt passt nicht zur gespeicherten Prüfsumme. Gespeichert: %s, "
            + "berechnet: %s.";
    static final String SNAPSHOT_DAMAGED = "Der Stand der Ausstellung von Rechnung %s wurde außerhalb der App "
            + "geändert. Die Seite der Rechnung zeigt, was abweicht.";
    static final String WRONG_SEAL = "%s: Das Siegel passt nicht. Prüfsumme oder Zuordnung der Datei wurden außerhalb "
            + "der App geändert.";

    private final StoredDocumentRepository documents;
    private final JsonMapper json;
    private final Seal seal;

    /**
     * Creates the store.
     *
     * @param documents the document table
     * @param json writes and reads the snapshots
     * @param seal seals the documents and snapshots
     */
    public DocumentStore(StoredDocumentRepository documents, JsonMapper json, Seal seal) {
        this.documents = documents;
        this.json = json;
        this.seal = seal;
    }

    /**
     * Saves the PDF and the XML of an issued invoice.
     *
     * @param invoiceId the invoice
     * @param number the invoice number, for the file names
     * @param files the files of the issue
     * @param at time of the issue
     */
    public void store(long invoiceId, String number, IssuedEInvoice files, Instant at) {
        documents.save(document(invoiceId, DocumentKind.PDF, number, files.pdf(), at));
        documents.save(document(invoiceId, DocumentKind.XML, number, files.xml(), at));
    }

    /**
     * Lists the documents of an invoice.
     *
     * @param invoiceId the invoice
     * @return the documents, PDF first; empty for a draft
     */
    public List<StoredDocument> list(long invoiceId) {
        return documents.findByInvoiceIdOrderByKindAsc(invoiceId);
    }

    /**
     * Returns one document of an invoice, after it passed the check.
     *
     * @param invoiceId the invoice
     * @param kind PDF or XML
     * @return the document, empty for a draft
     * @throws InvoiceException if the document does not match its SHA-256 or seal
     */
    public Optional<StoredDocument> find(long invoiceId, DocumentKind kind) {
        Optional<StoredDocument> document = documents.findByInvoiceIdAndKind(invoiceId, kind);
        document.ifPresent(this::verified);
        return document;
    }

    /**
     * Checks a document.
     *
     * @param document the document
     * @return the document
     * @throws InvoiceException if it does not match its SHA-256 or seal
     */
    public StoredDocument verified(StoredDocument document) {
        if (!problems(document).isEmpty()) {
            throw new InvoiceException(List.of(DAMAGED.formatted(document.filename())));
        }
        return document;
    }

    /**
     * Checks a document against its SHA-256 and seal.
     *
     * @param document the document
     * @return what is wrong, empty if it is in order
     */
    public List<String> problems(StoredDocument document) {
        List<String> problems = new ArrayList<>();
        String computed = Seal.sha256(document.content());
        if (!computed.equals(document.sha256())) {
            problems.add(WRONG_SHA.formatted(document.filename(), document.sha256(), computed));
        }
        if (!Seal.matches(document.seal(), seal.invoiceDocument(document.invoiceId(), document.kind().name(),
                document.sha256()))) {
            problems.add(WRONG_SEAL.formatted(document.filename()));
        }
        return problems;
    }

    /**
     * Tells whether the snapshot of an issued invoice still matches its seal.
     *
     * @param invoice the invoice
     * @return whether the stored seal is the one of its ID, number and snapshot
     */
    public boolean sealed(Invoice invoice) {
        return Seal.matches(documents.findSnapshotSeal(invoice.id()), seal(invoice.id(), invoice.number(),
                invoice.snapshot()));
    }

    /**
     * Reads the snapshot of an issued invoice back, after it passed the check of its seal.
     *
     * @param invoice the invoice
     * @return the invoice document as it was issued
     * @throws InvoiceException if the snapshot does not match its seal
     */
    public InvoiceDocument verifiedSnapshot(Invoice invoice) {
        if (!sealed(invoice)) {
            throw new InvoiceException(List.of(SNAPSHOT_DAMAGED.formatted(invoice.number())));
        }
        return restore(invoice.snapshot());
    }

    /**
     * Returns the seal of the snapshot of an issued invoice.
     *
     * @param invoiceId the invoice
     * @param number its number
     * @param snapshot the JSON
     * @return the seal
     */
    public String seal(long invoiceId, String number, String snapshot) {
        return seal.snapshot(invoiceId, number, snapshot);
    }

    /**
     * Returns the snapshot of an issued invoice document.
     *
     * @param document the document
     * @return the JSON
     */
    public String snapshot(InvoiceDocument document) {
        return json.writeValueAsString(document);
    }

    /**
     * Reads a snapshot back.
     *
     * @param snapshot the JSON
     * @return the invoice document as it was issued
     */
    public InvoiceDocument restore(String snapshot) {
        return json.readValue(snapshot, InvoiceDocument.class);
    }

    private StoredDocument document(long invoiceId, DocumentKind kind, String number, byte[] content, Instant at) {
        String sha256 = Seal.sha256(content);
        return new StoredDocument(null, invoiceId, kind, kind.filename(number), content, sha256, content.length, at,
                seal.invoiceDocument(invoiceId, kind.name(), sha256));
    }
}
