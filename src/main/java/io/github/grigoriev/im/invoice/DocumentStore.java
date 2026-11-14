package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.IssuedEInvoice;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Keeps what the issue freezes: the documents with their SHA-256, and the snapshot of the invoice as JSON. */
@Component
public class DocumentStore {

    private final StoredDocumentRepository documents;
    private final JsonMapper json;

    /**
     * Creates the store.
     *
     * @param documents the document table
     * @param json writes and reads the snapshots
     */
    public DocumentStore(StoredDocumentRepository documents, JsonMapper json) {
        this.documents = documents;
        this.json = json;
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
     * Returns one document of an invoice.
     *
     * @param invoiceId the invoice
     * @param kind PDF or XML
     * @return the document, empty for a draft
     */
    public Optional<StoredDocument> find(long invoiceId, DocumentKind kind) {
        return documents.findByInvoiceIdAndKind(invoiceId, kind);
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

    /**
     * Returns the SHA-256 of bytes.
     *
     * @param content the bytes
     * @return the hash, 64 hexadecimal characters
     */
    static String sha256(byte[] content) {
        return digest("SHA-256", content);
    }

    /**
     * Returns the hash of bytes.
     *
     * @param algorithm the algorithm
     * @param content the bytes
     * @return the hash, hexadecimal
     * @throws IllegalStateException if Java lacks the algorithm; every Java has SHA-256
     */
    static String digest(String algorithm, byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Java lacks " + algorithm, e);
        }
    }

    private static StoredDocument document(long invoiceId, DocumentKind kind, String number, byte[] content,
            Instant at) {
        return new StoredDocument(null, invoiceId, kind, kind.filename(number), content, sha256(content), content.length,
                at);
    }
}
