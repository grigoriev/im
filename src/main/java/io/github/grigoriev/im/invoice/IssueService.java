package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.EInvoiceException;
import io.github.grigoriev.im.einvoice.EInvoiceService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.IssuedEInvoice;
import io.github.grigoriev.im.numbering.ManualNumbers;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.customer.Customer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
                   seal = :seal, activity = :activity, updated_at = :at, version = version + 1
             WHERE id = :id AND version = :version AND status = 'DRAFT'""";
    static final String CANCEL = """
            UPDATE invoice SET status = 'CANCELLED', updated_at = :at, version = version + 1
             WHERE id = :id AND status IN ('ISSUED', 'SENT', 'PAID')""";
    static final String ALREADY_CANCELLED = "Die Rechnung %s ist schon storniert. Die Stornorechnung bleibt ein "
            + "Entwurf.";
    static final String NOT_VALID = "Die E-Rechnung hat die Prüfung nicht bestanden. Die Rechnung bleibt ein Entwurf.";
    static final String NUMBER_TAKEN = "Die Rechnungsnummer %s gibt es schon. Bitte das Nummernmuster oder die "
            + "Startnummer in den Einstellungen prüfen.";
    static final String EXTERNAL = "Eine externe Rechnung wird mit ihrer Datei erfasst, nicht ausgestellt.";
    static final String BY_HAND = "Für %d werden die Nummern von Hand erfasst. Eine Rechnung dieses Jahres kommt als "
            + "externe Rechnung mit ihrer Datei.";

    private static final Logger LOG = LoggerFactory.getLogger(IssueService.class);

    private final InvoiceService invoices;
    private final NumberService numbers;
    private final ManualNumbers manual;
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
     * @param manual tells a year before the app, whose numbers come by hand
     * @param einvoices produces and validates the e-invoice
     * @param store keeps the documents and the snapshot
     * @param jdbc writes the issue
     * @param audit the audit log
     * @param clock source of the time of the issue
     */
    @SuppressWarnings("java:S107") // the steps of the issue
    public IssueService(InvoiceService invoices, NumberService numbers, ManualNumbers manual, EInvoiceService einvoices,
            DocumentStore store, JdbcClient jdbc, AuditService audit, Clock clock) {
        this.invoices = invoices;
        this.numbers = numbers;
        this.manual = manual;
        this.einvoices = einvoices;
        this.store = store;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Issues a draft. A Stornorechnung cancels its invoice in the same transaction.
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
        if (draft.format().isExternal()) {
            throw new InvoiceException(List.of(EXTERNAL));
        }
        if (manual.isManual(draft.issueDate().getYear())) {
            throw new InvoiceException(List.of(BY_HAND.formatted(draft.issueDate().getYear())));
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
        frozen(draft, document, at);
        store.store(id, number, files, at);
        audit.add(id, AuditAction.INVOICE_ISSUED, number + ", "
                + Customer.label(document.buyer().name(), document.buyer().contactName()));
        return number;
    }

    /**
     * Freezes a draft with its number and snapshot. A Stornorechnung cancels its invoice in the same step. It runs in
     * the transaction that assigned the number, so a failure returns the number too.
     *
     * @param draft the draft as the transaction read it
     * @param document the invoice document with its number
     * @param at time of the issue
     * @throws InvoiceException if the number exists, the draft changed meanwhile or its invoice is cancelled
     * @throws org.springframework.transaction.IllegalTransactionStateException without a transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void freeze(Invoice draft, InvoiceDocument document, Instant at) {
        frozen(draft, document, at);
    }

    /** Freezes a draft in the running transaction: the body of {@link #freeze}, which the issue calls directly. */
    private void frozen(Invoice draft, InvoiceDocument document, Instant at) {
        long id = draft.id();
        String number = document.number();
        int updated;
        try {
            String snapshot = store.snapshot(document);
            updated = jdbc.sql(ISSUE).param("number", number).param("at", Timestamp.from(at))
                    .param("snapshot", snapshot).param("seal", store.seal(id, number, snapshot))
                    .param("activity", InvoiceService.activity(draft).name())
                    .param("id", id).param("version", draft.version()).update();
        } catch (DuplicateKeyException _) {
            throw new InvoiceException(List.of(NUMBER_TAKEN.formatted(number)));
        }
        if (updated == 0) {
            // Another window issued or saved the draft after this transaction read it.
            throw new InvoiceException(List.of(InvoiceService.CHANGED_ELSEWHERE));
        }
        Optional<Invoice> cancelled = invoices.cancelled(draft);
        if (cancelled.isPresent()) {
            Invoice original = cancelled.get();
            // The same transaction cancels the invoice: a failure leaves both as they were, the number included.
            if (jdbc.sql(CANCEL).param("at", Timestamp.from(at)).param("id", original.id()).update() == 0) {
                throw new InvoiceException(List.of(ALREADY_CANCELLED.formatted(original.number())));
            }
            audit.add(original.id(), AuditAction.INVOICE_CANCELLED, original.number() + ", durch Stornorechnung "
                    + number);
        }
    }
}
