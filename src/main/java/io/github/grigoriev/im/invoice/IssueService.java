package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.EInvoiceException;
import io.github.grigoriev.im.einvoice.EInvoiceService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.IssuedEInvoice;
import io.github.grigoriev.im.numbering.NumberService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues a complete draft (docs/PLAN.md, section 6). One transaction assigns the number, freezes the snapshot,
 * produces and validates the e-invoice and stores its documents. Any failure rolls all of it back, the number
 * included, so no gap remains.
 */
@Service
public class IssueService {

    static final String ISSUE = """
            UPDATE invoice SET status = 'ISSUED', number = :number, issued_at = :at, snapshot = :snapshot,
                   updated_at = :at, version = version + 1
             WHERE id = :id AND version = :version AND status = 'DRAFT'""";
    static final String NOT_VALID = "Die E-Rechnung hat die Prüfung nicht bestanden. Die Rechnung bleibt ein Entwurf.";
    static final String NUMBER_TAKEN = "Die Rechnungsnummer %s gibt es schon. Bitte das Nummernmuster oder die "
            + "Startnummer in den Einstellungen prüfen.";

    private static final Logger LOG = LoggerFactory.getLogger(IssueService.class);

    private final InvoiceService invoices;
    private final NumberService numbers;
    private final EInvoiceService einvoices;
    private final DocumentStore store;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param invoices the drafts
     * @param numbers assigns the number
     * @param einvoices produces and validates the e-invoice
     * @param store keeps the documents and the snapshot
     * @param jdbc writes the issue
     * @param audit the audit log
     * @param clock source of the time of the issue
     */
    public IssueService(InvoiceService invoices, NumberService numbers, EInvoiceService einvoices, DocumentStore store,
            JdbcClient jdbc, AuditService audit, Clock clock) {
        this.invoices = invoices;
        this.numbers = numbers;
        this.einvoices = einvoices;
        this.store = store;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Issues a draft.
     *
     * @param id the draft
     * @param version the version the user saw; a newer one means a change in another window
     * @return the invoice number
     * @throws InvoiceException if the draft is unknown, issued, changed elsewhere, incomplete or not valid
     */
    @Transactional
    public String issue(long id, Long version) {
        Invoice draft = invoices.draft(id);
        if (version == null || !version.equals(draft.version())) {
            throw new InvoiceException(List.of(InvoiceService.CHANGED_ELSEWHERE));
        }
        List<String> problems = invoices.problems(draft);
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        String number = numbers.assign(draft.issueDate());
        InvoiceDocument document = invoices.document(draft, number).orElseThrow();
        IssuedEInvoice files;
        try {
            files = einvoices.issue(document);
        } catch (EInvoiceException e) {
            LOG.warn("The issue of draft {} failed: {}", id, e.getMessage());
            throw new InvoiceException(List.of(NOT_VALID));
        }
        Instant at = clock.instant();
        int updated;
        try {
            updated = jdbc.sql(ISSUE).param("number", number).param("at", Timestamp.from(at))
                    .param("snapshot", store.snapshot(document)).param("id", id).param("version", version).update();
        } catch (DuplicateKeyException _) {
            throw new InvoiceException(List.of(NUMBER_TAKEN.formatted(number)));
        }
        if (updated == 0) {
            // Another window issued or saved the draft after this transaction read it.
            throw new InvoiceException(List.of(InvoiceService.CHANGED_ELSEWHERE));
        }
        store.store(id, number, files, at);
        audit.add(id, AuditAction.INVOICE_ISSUED, number + ", " + document.buyer().name());
        return number;
    }
}
