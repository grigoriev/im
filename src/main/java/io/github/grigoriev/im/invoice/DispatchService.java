package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.validation.Fields;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the dispatches of an issued invoice (docs/PLAN.md, section 7). The first one moves the status from
 * {@code ISSUED} to {@code SENT}; a later status stays. The history of the invoice shows each dispatch with its note.
 */
@Service
public class DispatchService {

    static final String SENT = """
            UPDATE invoice SET status = 'SENT', updated_at = :at, version = version + 1
             WHERE id = :id AND status = 'ISSUED'""";
    static final String NOT_ISSUED = "Erst ausstellen, dann versenden.";
    static final String CANCELLED = "Eine stornierte Rechnung wird nicht mehr versendet.";
    static final int MAX_RECIPIENT = 200;
    static final int MAX_NOTE = 500;

    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceService invoices;
    private final DispatchRepository dispatches;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param invoices the invoices
     * @param dispatches the dispatches
     * @param jdbc moves the status
     * @param audit the audit log and the history of the invoice
     * @param clock source of today and of the time of the record
     */
    public DispatchService(InvoiceService invoices, DispatchRepository dispatches, JdbcClient jdbc, AuditService audit,
            Clock clock) {
        this.invoices = invoices;
        this.dispatches = dispatches;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Records a dispatch the user did outside the app: own mail program, post, portal or in person.
     *
     * @param invoiceId the invoice
     * @param form the entered values
     * @return the saved dispatch
     * @throws InvoiceException if the invoice is unknown, a draft or cancelled, or a value is wrong
     */
    @Transactional
    public Dispatch recordManual(long invoiceId, DispatchForm form) {
        Invoice invoice = invoices.find(invoiceId).orElseThrow(() -> new InvoiceException(List.of(InvoiceService.UNKNOWN)));
        if (invoice.isDraft()) {
            throw new InvoiceException(List.of(NOT_ISSUED));
        }
        if (invoice.status() == InvoiceStatus.CANCELLED) {
            throw new InvoiceException(List.of(CANCELLED));
        }
        String recipient = Fields.text(form.getRecipient());
        String note = Fields.text(form.getNote());
        List<String> problems = new ArrayList<>();
        LocalDate sentOn = form.getSentOn();
        if (sentOn == null) {
            problems.add("Bitte das Versanddatum angeben.");
        } else if (sentOn.isAfter(today())) {
            problems.add("Das Versanddatum liegt in der Zukunft.");
        } else if (sentOn.isBefore(invoice.issueDate())) {
            problems.add("Das Versanddatum liegt vor dem Rechnungsdatum.");
        }
        if (form.getChannel() == null || !form.getChannel().isManual()) {
            problems.add("Bitte den Weg wählen.");
        }
        Fields.optional(problems, recipient, "Der Empfänger", MAX_RECIPIENT);
        Fields.optional(problems, note, "Die Notiz", MAX_NOTE);
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        Instant now = clock.instant();
        Dispatch saved = dispatches.save(new Dispatch(null, invoiceId, sentOn, form.getChannel(), recipient, note,
                AuditService.currentUser(), now));
        jdbc.sql(SENT).param("at", Timestamp.from(now)).param("id", invoiceId).update();
        audit.add(invoiceId, AuditAction.INVOICE_SENT, invoice.number() + ", " + GERMAN.format(sentOn) + ", "
                + form.getChannel().label() + (recipient == null ? "" : ", " + recipient)
                + (note == null ? "" : ", Notiz: " + note));
        return saved;
    }

    /**
     * Returns today, the default day of a dispatch and its latest allowed one.
     *
     * @return today
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Returns the dispatches of an invoice.
     *
     * @param invoiceId the invoice
     * @return its dispatches, oldest first
     */
    public List<Dispatch> list(long invoiceId) {
        return dispatches.findByInvoiceIdOrderBySentOnAscIdAsc(invoiceId);
    }
}
