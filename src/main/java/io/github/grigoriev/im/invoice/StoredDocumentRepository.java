package io.github.grigoriev.im.invoice;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the documents of issued invoices. They are written once. */
interface StoredDocumentRepository extends ListCrudRepository<StoredDocument, Long> {

    /**
     * Lists the documents of an invoice.
     *
     * @param invoiceId the invoice
     * @return the documents, PDF first (by kind)
     */
    List<StoredDocument> findByInvoiceIdOrderByKindAsc(Long invoiceId);

    /**
     * Returns one document of an invoice.
     *
     * @param invoiceId the invoice
     * @param kind PDF or XML
     * @return the document, if the invoice is issued
     */
    Optional<StoredDocument> findByInvoiceIdAndKind(Long invoiceId, DocumentKind kind);
}
