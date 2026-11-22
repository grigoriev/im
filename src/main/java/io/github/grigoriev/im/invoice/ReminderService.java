package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.validation.Fields;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the payment reminders of an issued invoice that waits for its money (docs/PLAN.md, section 7): sent from
 * the app or noted by hand. A reminder changes nothing of the invoice; the history shows each one with its note.
 */
@Service
public class ReminderService {

    static final String NOTHING_OPEN = "Für diese Rechnung ist nichts offen.";
    static final String NOT_ISSUED = "Erst ausstellen, dann erinnern.";
    static final String CANCELLED = "Eine stornierte Rechnung braucht keine Erinnerung.";
    static final String STORNO = "Eine Stornorechnung braucht keine Erinnerung.";
    static final int MAX_NOTE = 500;
    static final String LAST = "SELECT invoice_id, max(reminded_on) FROM reminder GROUP BY invoice_id";

    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceService invoices;
    private final PaymentService payments;
    private final ReminderRepository reminders;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param invoices the invoices
     * @param payments the open amount
     * @param reminders the reminders
     * @param jdbc the last reminder of each invoice
     * @param audit the audit log and the history of the invoice
     * @param clock source of today and of the time of the record
     */
    public ReminderService(InvoiceService invoices, PaymentService payments, ReminderRepository reminders,
            JdbcClient jdbc, AuditService audit, Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.reminders = reminders;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Returns an invoice that a reminder may concern: issued, not canceled, no Stornorechnung, with money open.
     *
     * @param invoiceId the invoice
     * @return the invoice
     * @throws InvoiceException if the invoice is unknown or waits for nothing
     */
    public Invoice remindable(long invoiceId) {
        Invoice invoice = invoices.find(invoiceId).orElseThrow(() -> new InvoiceException(List.of(InvoiceService.UNKNOWN)));
        if (invoice.isDraft()) {
            throw new InvoiceException(List.of(NOT_ISSUED));
        }
        if (invoice.status() == InvoiceStatus.CANCELLED) {
            throw new InvoiceException(List.of(CANCELLED));
        }
        if (invoices.cancelled(invoice).isPresent()) {
            throw new InvoiceException(List.of(STORNO));
        }
        if (open(invoice).signum() <= 0) {
            throw new InvoiceException(List.of(NOTHING_OPEN));
        }
        return invoice;
    }

    /**
     * Tells whether a reminder may concern an invoice, for the page of the invoice.
     *
     * @param invoiceId the invoice
     * @return {@code true} if {@link #remindable(long)} accepts it
     */
    public boolean isRemindable(long invoiceId) {
        try {
            remindable(invoiceId);
            return true;
        } catch (InvoiceException _) {
            return false;
        }
    }

    /**
     * Tells whether the due date of an invoice has passed.
     *
     * @param invoice the issued invoice
     * @return {@code true} from the day after the due date
     */
    public boolean overdue(Invoice invoice) {
        return invoice.dueDate().isBefore(today());
    }

    /**
     * Returns what an invoice still waits for.
     *
     * @param invoice the issued invoice
     * @return the open amount
     */
    public BigDecimal open(Invoice invoice) {
        return payments.summary(invoice).open();
    }

    /**
     * Records a reminder the user gave outside the app, for example by phone or letter.
     *
     * @param invoiceId the invoice
     * @param form the entered values
     * @return the saved reminder
     * @throws InvoiceException if the invoice waits for nothing or a value is wrong
     */
    @Transactional
    public Reminder recordManual(long invoiceId, ReminderForm form) {
        Invoice invoice = remindable(invoiceId);
        String note = Fields.text(form.getNote());
        List<String> problems = new ArrayList<>();
        LocalDate remindedOn = form.getRemindedOn();
        if (remindedOn == null) {
            problems.add("Bitte das Datum der Erinnerung angeben.");
        } else if (remindedOn.isAfter(today())) {
            problems.add("Das Datum der Erinnerung liegt in der Zukunft.");
        } else if (remindedOn.isBefore(invoice.issueDate())) {
            problems.add("Das Datum der Erinnerung liegt vor dem Rechnungsdatum.");
        }
        Fields.optional(problems, note, "Die Notiz", MAX_NOTE);
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        return save(invoice, remindedOn, ReminderChannel.MANUAL, null, note);
    }

    /**
     * Records a reminder the app sent, today. The check came before the send: a payment that arrived meanwhile does not
     * keep the sent e-mail out of the history.
     *
     * @param invoiceId the invoice
     * @param recipient the address
     * @return the saved reminder
     * @throws InvoiceException if the invoice is unknown
     */
    @Transactional
    public Reminder recordEmail(long invoiceId, String recipient) {
        Invoice invoice = invoices.find(invoiceId).orElseThrow(() -> new InvoiceException(List.of(InvoiceService.UNKNOWN)));
        return save(invoice, today(), ReminderChannel.EMAIL_APP, recipient, null);
    }

    private Reminder save(Invoice invoice, LocalDate remindedOn, ReminderChannel channel, String recipient,
            String note) {
        Reminder saved = reminders.save(new Reminder(null, invoice.id(), remindedOn, channel, recipient, note,
                AuditService.currentUser(), clock.instant()));
        audit.add(invoice.id(), AuditAction.REMINDER_RECORDED, invoice.number() + ", " + GERMAN.format(remindedOn)
                + ", " + channel.label() + (recipient == null ? "" : ", " + recipient)
                + (note == null ? "" : ", Notiz: " + note));
        return saved;
    }

    /**
     * Returns the reminders of an invoice.
     *
     * @param invoiceId the invoice
     * @return its reminders, oldest first
     */
    public List<Reminder> list(long invoiceId) {
        return reminders.findByInvoiceIdOrderByRemindedOnAscIdAsc(invoiceId);
    }

    /**
     * Returns the day of the last reminder of each invoice that has one.
     *
     * @return the last day by invoice ID
     */
    public Map<Long, LocalDate> last() {
        Map<Long, LocalDate> last = new HashMap<>();
        jdbc.sql(LAST).query(rs -> {
            last.put(rs.getLong(1), rs.getObject(2, LocalDate.class));
        });
        return last;
    }

    /**
     * Returns today, the default day of a reminder and its latest allowed one.
     *
     * @return today
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
