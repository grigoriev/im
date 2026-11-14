package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.validation.Fields;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the payments and refunds of an issued invoice (docs/PLAN.md, section 7). The status follows the sum: it is
 * {@code PAID} while the payments cover the total, else {@code SENT} after a dispatch and {@code ISSUED} before. A
 * cancelled invoice keeps its status and takes refunds only.
 */
@Service
public class PaymentService {

    static final String LOCK = "SELECT id FROM invoice WHERE id = :id FOR UPDATE";
    static final String STATUS = """
            UPDATE invoice SET status = :status, updated_at = :at, version = version + 1
             WHERE id = :id AND status <> :status AND status <> 'CANCELLED'""";
    static final String NOT_ISSUED = "Erst ausstellen, dann Zahlungen erfassen.";
    static final String CANCELLED = "Eine stornierte Rechnung bekommt nur noch Rückzahlungen, mit Minus.";
    static final String ON_ORIGINAL = "Eine Stornorechnung bekommt keine Zahlungen. Erstattungen gehören zur "
            + "stornierten Rechnung.";
    static final int MAX_NOTE = 500;
    static final int MAX_INTEGER_DIGITS = 10;
    static final int MAX_DECIMALS = 2;

    private static final String AMOUNT = "Der Betrag";
    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * The payments of an invoice and what they leave open.
     *
     * @param entries the payments, oldest first
     * @param paid their sum
     * @param open the total minus the sum, negative if overpaid
     */
    public record Payments(List<Payment> entries, BigDecimal paid, BigDecimal open) {

        /** Copies the list. */
        public Payments {
            entries = List.copyOf(entries);
        }
    }

    private final InvoiceService invoices;
    private final PaymentRepository payments;
    private final DispatchRepository dispatches;
    private final DocumentStore documents;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param invoices the invoices
     * @param payments the payments
     * @param dispatches tells whether an invoice went out
     * @param documents the snapshot with the total
     * @param jdbc locks the invoice and moves the status
     * @param audit the audit log and the history of the invoice
     * @param clock source of today and of the time of the record
     */
    public PaymentService(InvoiceService invoices, PaymentRepository payments, DispatchRepository dispatches,
            DocumentStore documents, JdbcClient jdbc, AuditService audit, Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.dispatches = dispatches;
        this.documents = documents;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Records a payment or, with a negative amount, a refund. It locks the invoice, so two records never race for
     * the status.
     *
     * @param invoiceId the invoice
     * @param form the entered values
     * @return the status of the invoice afterward
     * @throws InvoiceException if the invoice is unknown or a draft, or a value is wrong
     */
    @Transactional
    public InvoiceStatus add(long invoiceId, PaymentForm form) {
        Invoice invoice = invoices.find(invoiceId).orElseThrow(() -> new InvoiceException(List.of(InvoiceService.UNKNOWN)));
        if (invoice.isDraft()) {
            throw new InvoiceException(List.of(NOT_ISSUED));
        }
        if (invoices.cancelled(invoice).isPresent()) {
            throw new InvoiceException(List.of(ON_ORIGINAL));
        }
        jdbc.sql(LOCK).param("id", invoiceId).query(Long.class).single();
        String note = Fields.text(form.getNote());
        List<String> problems = new ArrayList<>();
        LocalDate paidOn = form.getPaidOn();
        if (paidOn == null) {
            problems.add("Bitte den Tag des Zahlungseingangs angeben.");
        } else if (paidOn.isAfter(today())) {
            problems.add("Der Zahlungseingang liegt in der Zukunft.");
        } else if (paidOn.isBefore(invoice.issueDate())) {
            problems.add("Der Zahlungseingang liegt vor dem Rechnungsdatum.");
        }
        BigDecimal amount = amount(problems, form.getAmount());
        Fields.optional(problems, note, "Die Notiz", MAX_NOTE);
        Payments before = summary(invoice);
        if (amount != null) {
            if (invoice.status() == InvoiceStatus.CANCELLED && amount.signum() == 1) {
                problems.add(CANCELLED);
            } else if (before.paid().add(amount).signum() < 0) {
                problems.add("Die Rückzahlung ist höher als die Zahlungen bisher (" + euros(before.paid()) + ").");
            }
        }
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        Instant now = clock.instant();
        payments.save(new Payment(null, invoiceId, paidOn, amount, note, AuditService.currentUser(), now));
        audit.add(invoiceId, AuditAction.PAYMENT_RECORDED, invoice.number() + ", " + GERMAN.format(paidOn) + ", "
                + euros(amount) + (note == null ? "" : ", Notiz: " + note));
        if (invoice.status() == InvoiceStatus.CANCELLED) {
            return InvoiceStatus.CANCELLED;
        }
        InvoiceStatus status = status(invoiceId, before.open().subtract(amount));
        jdbc.sql(STATUS).param("status", status.name()).param("at", Timestamp.from(now)).param("id", invoiceId).update();
        return status;
    }

    /** The status that fits the open amount: paid, else sent after a dispatch, else issued. */
    private InvoiceStatus status(long invoiceId, BigDecimal open) {
        if (open.signum() <= 0) {
            return InvoiceStatus.PAID;
        }
        return dispatches.existsByInvoiceId(invoiceId) ? InvoiceStatus.SENT : InvoiceStatus.ISSUED;
    }

    /**
     * Returns the payments of an issued invoice and what they leave open.
     *
     * @param invoice the issued invoice
     * @return the payments, their sum and the open amount
     */
    public Payments summary(Invoice invoice) {
        List<Payment> entries = payments.findByInvoiceIdOrderByPaidOnAscIdAsc(invoice.id());
        BigDecimal paid = entries.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Payments(entries, paid, documents.restore(invoice.snapshot()).total().subtract(paid));
    }

    /**
     * Returns today, the default day of a payment and its latest allowed one.
     *
     * @return today
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Reads the amount: required, not 0, at most 2 decimals and 10 digits before the comma. */
    private static BigDecimal amount(List<String> problems, String text) {
        if (text == null || text.isBlank()) {
            problems.add("Bitte den Betrag angeben.");
            return null;
        }
        if (DraftForm.ambiguous(text)) {
            problems.add(AMOUNT + " ist mehrdeutig: bitte mit Komma schreiben, zum Beispiel 1.500,00.");
            return null;
        }
        BigDecimal value = DraftForm.parse(text);
        if (value == null) {
            problems.add(AMOUNT + " ist keine Zahl, zum Beispiel 400,00 oder -50,00.");
        } else if (value.signum() == 0) {
            problems.add(AMOUNT + " ist 0.");
        } else if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            problems.add(AMOUNT + " hat höchstens " + MAX_INTEGER_DIGITS + " Stellen vor dem Komma.");
        } else if (value.stripTrailingZeros().scale() > MAX_DECIMALS) {
            problems.add(AMOUNT + " hat höchstens " + MAX_DECIMALS + " Nachkommastellen.");
        } else {
            return value.setScale(MAX_DECIMALS, RoundingMode.UNNECESSARY);
        }
        return null;
    }

    /** Writes an amount German style: {@code 1.200,00 €}. */
    static String euros(BigDecimal amount) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(amount) + " €";
    }
}
