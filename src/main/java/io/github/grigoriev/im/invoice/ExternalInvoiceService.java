package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.numbering.ManualNumbers;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SettingsException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers external invoices (docs/PLAN.md, section 7, "External invoices"). A draft of format {@code EXTERNAL} holds
 * the facts typed in by hand. Its registration takes the next number under the row lock of the issue, compares it with
 * the number of the file, freezes the snapshot and keeps the original file, all in one transaction. In a year before
 * the app ({@link ManualNumbers}) the number of the file is checked instead: its form, year and that it is free.
 */
@Service
public class ExternalInvoiceService {

    static final String NOT_EXTERNAL = "Erfassen lässt sich nur eine externe Rechnung.";
    static final String NUMBER_MISSING = "Bitte die Rechnungsnummer aus der Datei angeben.";
    static final String NUMBER_DIFFERS = "Die nächste Rechnungsnummer ist %s, angegeben ist %s. Bitte die Nummer in "
            + "der Datei korrigieren. Es wurde nichts gespeichert.";
    static final String NO_ORIGINALS = "Dateien gehören nur zu einer erfassten externen Rechnung.";

    private final InvoiceService invoices;
    private final IssueService issues;
    private final NumberService numbers;
    private final ManualNumbers manual;
    private final OriginalStore originals;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param invoices the drafts
     * @param issues freezes the draft as the issue does
     * @param numbers assigns the number
     * @param manual checks a number by hand in a year before the app
     * @param originals keeps the original files
     * @param audit the audit log
     * @param clock source of the time of the registration
     */
    public ExternalInvoiceService(InvoiceService invoices, IssueService issues, NumberService numbers,
            ManualNumbers manual, OriginalStore originals, AuditService audit, Clock clock) {
        this.invoices = invoices;
        this.issues = issues;
        this.numbers = numbers;
        this.manual = manual;
        this.originals = originals;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Returns the number the registration of a draft would take now.
     *
     * @param draft the draft
     * @return the next number of the year of its invoice date, empty in a year by hand: the file names it
     */
    public Optional<String> nextNumber(Invoice draft) {
        int year = draft.issueDate().getYear();
        return manual.isManual(year) ? Optional.empty() : Optional.of(numbers.next(year));
    }

    /**
     * Registers an external invoice: its number, its frozen snapshot and its original file.
     *
     * @param id the draft
     * @param version the version the user saw; a newer one means a change in another window
     * @param fileNumber the number printed on the file
     * @param filename the name of the upload, may be {@code null}
     * @param content the bytes of the file
     * @return the invoice number
     * @throws InvoiceException if the draft is unknown, issued, not external, changed elsewhere or incomplete, if the
     *     file is missing or of another type, or if the number of the file is not the next one
     */
    @Transactional
    public String register(long id, Long version, String fileNumber, String filename, byte[] content) {
        Invoice draft = invoices.draft(id);
        if (version == null || !version.equals(draft.version())) {
            throw new InvoiceException(List.of(InvoiceService.CHANGED_ELSEWHERE));
        }
        if (!draft.format().isExternal()) {
            throw new InvoiceException(List.of(NOT_EXTERNAL));
        }
        List<String> problems = new ArrayList<>(invoices.problems(draft));
        String entered = fileNumber == null ? "" : fileNumber.strip();
        if (entered.isEmpty()) {
            problems.add(NUMBER_MISSING);
        }
        Optional<String> type = OriginalStore.mediaType(problems, content);
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        String number = number(draft, entered);
        InvoiceDocument document = invoices.document(draft, number).orElseThrow();
        Instant at = clock.instant();
        issues.freeze(draft, document, at);
        InvoiceOriginal original = originals.store(id, number, filename, content, type.orElseThrow(), at);
        audit.add(id, AuditAction.INVOICE_REGISTERED, number + ", "
                + Customer.label(document.buyer().name(), document.buyer().contactName()) + ", " + original.filename()
                + " (SHA-256 " + original.sha256() + ")");
        return number;
    }

    /** The number of the file, checked by hand in a year before the app, else against the counter. */
    private String number(Invoice draft, String entered) {
        Optional<String> byHand;
        try {
            byHand = manual.claim(draft.issueDate(), entered);
        } catch (SettingsException e) {
            throw new InvoiceException(e.problems());
        }
        if (byHand.isPresent()) {
            return byHand.get();
        }
        // The exception rolls the transaction back, and with it the counter: no gap remains.
        String number = numbers.assign(draft.issueDate());
        if (!number.equals(entered)) {
            throw new InvoiceException(List.of(NUMBER_DIFFERS.formatted(number, entered)));
        }
        return number;
    }

    /**
     * Adds a further original file to a registered external invoice, for example the PDF of an Excel invoice.
     *
     * @param id the invoice
     * @param filename the name of the upload, may be {@code null}
     * @param content the bytes of the file
     * @return the saved file
     * @throws InvoiceException if the invoice is unknown, a draft or no external invoice, or the file is missing or of
     *     another type
     */
    @Transactional
    public InvoiceOriginal addOriginal(long id, String filename, byte[] content) {
        Invoice invoice = invoices.find(id).orElseThrow(() -> new InvoiceException(List.of(InvoiceService.UNKNOWN)));
        if (invoice.isDraft() || !invoice.format().isExternal()) {
            throw new InvoiceException(List.of(NO_ORIGINALS));
        }
        List<String> problems = new ArrayList<>();
        Optional<String> type = OriginalStore.mediaType(problems, content);
        if (type.isEmpty()) {
            throw new InvoiceException(problems);
        }
        InvoiceOriginal original = originals.store(id, invoice.number(), filename, content, type.get(),
                clock.instant());
        audit.add(id, AuditAction.ORIGINAL_ADDED, original.filename() + " (SHA-256 " + original.sha256() + ")");
        return original;
    }
}
