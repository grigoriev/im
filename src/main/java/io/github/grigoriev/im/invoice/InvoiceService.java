package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.TaxNoteService;
import io.github.grigoriev.im.seller.TaxStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, changes and deletes draft invoices, and tells what a draft still needs before its issue. A draft
 * may be incomplete; the issue (M2d) refuses it until {@link #problems(Invoice)} is empty.
 */
@Service
public class InvoiceService {

    static final int DEFAULT_PAYMENT_DAYS = 14;
    /** The number of a draft in its texts and its preview. */
    static final String DRAFT_NUMBER = "Entwurf";
    static final int MAX_PAYMENT_DAYS = 365;
    static final int MAX_TEXT = 2000;
    static final int MAX_TAX_NOTE = 1000;
    static final int MAX_REFERENCE = 200;
    static final String CHANGED_ELSEWHERE = "Der Entwurf wurde inzwischen in einem anderen Fenster geändert. "
            + "Bitte die Seite neu laden.";
    static final String DEFAULT_INTRO = "Sehr geehrte Damen und Herren,\n\nvielen Dank für Ihren Auftrag.";
    static final String DEFAULT_CLOSING = "Mit freundlichen Grüßen";

    private final InvoiceRepository invoices;
    private final TemplateRepository templates;
    private final CustomerService customers;
    private final SellerService sellers;
    private final TaxNoteService notes;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param invoices the invoice store
     * @param templates the templates of the customers
     * @param customers the customers
     * @param sellers the seller profile
     * @param notes the default tax notes
     * @param audit the audit log
     * @param clock source of today
     */
    public InvoiceService(InvoiceRepository invoices, TemplateRepository templates, CustomerService customers,
            SellerService sellers, TaxNoteService notes, AuditService audit, Clock clock) {
        this.invoices = invoices;
        this.templates = templates;
        this.customers = customers;
        this.sellers = sellers;
        this.notes = notes;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Lists all invoices, the newest first.
     *
     * @return the invoices
     */
    public List<Invoice> list() {
        return invoices.findAllByOrderByIdDesc();
    }

    /**
     * Returns an invoice.
     *
     * @param id the ID
     * @return the invoice, if it exists
     */
    public Optional<Invoice> find(long id) {
        return invoices.findById(id);
    }

    /**
     * Returns the tax category of a new line: the one that fits the tax status of the seller.
     *
     * @return {@link TaxCategory#KLEINUNTERNEHMER} or {@link TaxCategory#STANDARD_19}
     */
    public TaxCategory defaultCategory() {
        return sellers.find().map(SellerProfile::taxStatus).orElse(TaxStatus.KLEINUNTERNEHMER) == TaxStatus.REGULAR
                ? TaxCategory.STANDARD_19 : TaxCategory.KLEINUNTERNEHMER;
    }

    /**
     * Creates a draft for a customer, today's dates and the customer's buyer reference. Without a template it takes the
     * customer's format, one empty line and the tax note of the seller's tax status. With a template it copies the
     * template's format, texts, payment days and lines; a later change of the template leaves the draft as it is.
     *
     * @param customerId the customer
     * @param templateId the template, {@code null} for none
     * @return the saved draft
     * @throws InvoiceException without a seller profile, customer or template of this customer
     */
    @Transactional
    public Invoice createDraft(long customerId, Long templateId) {
        if (sellers.find().isEmpty()) {
            throw new InvoiceException(List.of("Bitte zuerst das Profil in den Einstellungen ausfüllen."));
        }
        Customer customer = customers.find(customerId)
                .orElseThrow(() -> new InvoiceException(List.of("Diesen Kunden gibt es nicht.")));
        LocalDate today = LocalDate.now(clock);
        Invoice draft;
        if (templateId == null) {
            TaxCategory category = defaultCategory();
            draft = new Invoice(null, InvoiceStatus.DRAFT, customerId, null, customer.format(), today, today, today,
                    DEFAULT_PAYMENT_DAYS, customer.buyerReference(), DEFAULT_INTRO, DEFAULT_CLOSING,
                    notes.note(category), List.of(DraftLine.empty(category)), clock.instant(), clock.instant(), null);
        } else {
            InvoiceTemplate template = templates.findById(templateId)
                    .filter(found -> found.customerId() == customerId)
                    .orElseThrow(() -> new InvoiceException(List.of("Diese Vorlage gibt es nicht.")));
            draft = new Invoice(null, InvoiceStatus.DRAFT, customerId, null, template.format(), today, today, today,
                    template.paymentDays(), customer.buyerReference(), template.intro(), template.closing(),
                    template.taxNote(), template.lines().stream().map(TemplateLine::toDraftLine).toList(),
                    clock.instant(), clock.instant(), null);
        }
        Invoice saved = invoices.save(draft);
        audit.add(AuditAction.DRAFT_CREATED, customer.name());
        return saved;
    }

    /**
     * Saves a draft as entered. A draft may be incomplete; only unreadable or too long values are refused.
     *
     * @param id the draft
     * @param form the entered values
     * @return the saved draft
     * @throws InvoiceException with every problem, or if the invoice is no draft
     */
    @Transactional
    public Invoice saveDraft(long id, DraftForm form) {
        Invoice draft = draft(id);
        if (form.getVersion() == null || !form.getVersion().equals(draft.version())) {
            throw new InvoiceException(List.of(CHANGED_ELSEWHERE));
        }
        List<String> problems = new ArrayList<>();
        List<DraftLine> lines = new ArrayList<>();
        for (int i = 0; i < form.getLines().size(); i++) {
            lines.add(form.getLines().get(i).toLine(i + 1, problems));
        }
        LineForm.length(problems, form.getBuyerReference(), "Die Käuferreferenz", MAX_REFERENCE);
        LineForm.length(problems, form.getIntro(), "Die Einleitung", MAX_TEXT);
        LineForm.length(problems, form.getClosing(), "Der Schlusstext", MAX_TEXT);
        LineForm.length(problems, form.getTaxNote(), "Der Steuerhinweis", MAX_TAX_NOTE);
        if (form.getPaymentDays() < 0 || form.getPaymentDays() > MAX_PAYMENT_DAYS) {
            problems.add("Das Zahlungsziel liegt zwischen 0 und " + MAX_PAYMENT_DAYS + " Tagen.");
        }
        if (form.getFormat() == null || form.getIssueDate() == null || form.getServiceFrom() == null
                || form.getServiceTo() == null) {
            problems.add("Bitte Format, Rechnungsdatum und Leistungszeitraum angeben.");
        }
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        try {
            return invoices.save(new Invoice(draft.id(), draft.status(), draft.customerId(), null, form.getFormat(),
                    form.getIssueDate(), form.getServiceFrom(), form.getServiceTo(), form.getPaymentDays(),
                    text(form.getBuyerReference()), text(form.getIntro()), text(form.getClosing()),
                    text(form.getTaxNote()), lines, draft.createdAt(), clock.instant(), draft.version()));
        } catch (OptimisticLockingFailureException _) {
            // Two saves of one version at the same moment: the database lets the first win.
            throw new InvoiceException(List.of(CHANGED_ELSEWHERE));
        }
    }

    /**
     * Deletes a draft. An issued invoice stays: it is immutable.
     *
     * @param id the draft
     * @throws InvoiceException if the invoice is no draft
     */
    @Transactional
    public void deleteDraft(long id) {
        Invoice draft = draft(id);
        invoices.deleteById(draft.id());
        audit.add(AuditAction.DRAFT_DELETED, customerName(draft));
    }

    /**
     * Returns the name of the invoice's customer.
     *
     * @param invoice the invoice
     * @return the name, or a dash if the customer is gone
     */
    public String customerName(Invoice invoice) {
        return customers.find(invoice.customerId()).map(Customer::name).orElse("–");
    }

    /**
     * Tells what a draft still needs before its issue: the rules of EN 16931 and of the seller's tax status.
     *
     * @param invoice the draft
     * @return the problems in German, empty if the draft is complete
     */
    public List<String> problems(Invoice invoice) {
        List<String> problems = new ArrayList<>();
        Optional<SellerProfile> seller = sellers.find();
        Optional<Customer> customer = customers.find(invoice.customerId());
        if (seller.isEmpty()) {
            problems.add("Das Profil in den Einstellungen fehlt.");
        }
        if (customer.isEmpty()) {
            problems.add("Den Kunden gibt es nicht mehr.");
        }
        lineProblems(invoice, problems);
        if (invoice.serviceTo().isBefore(invoice.serviceFrom())) {
            problems.add("Der Leistungszeitraum endet vor seinem Beginn.");
        }
        taxProblems(invoice, seller, customer, problems);
        if (invoice.format().followsXRechnung()) {
            if (invoice.buyerReference() == null) {
                problems.add("Die XRechnung braucht eine Käuferreferenz, zum Beispiel die Leitweg-ID (BR-DE-15).");
            }
            if (customer.isPresent() && customer.get().email() == null) {
                problems.add("Die XRechnung braucht die E-Mail-Adresse des Kunden. Bitte beim Kunden eintragen.");
            }
        }
        return problems;
    }

    private static void lineProblems(Invoice invoice, List<String> problems) {
        if (invoice.lines().isEmpty()) {
            problems.add("Die Rechnung braucht mindestens eine Position.");
        }
        for (int i = 0; i < invoice.lines().size(); i++) {
            DraftLine line = invoice.lines().get(i);
            if (line.text() == null || line.text().isBlank()) {
                problems.add(LineForm.position(i + 1) + "Bitte die Leistung beschreiben.");
            }
            if (line.quantity().multiply(line.unitPrice()).signum() <= 0) {
                problems.add(LineForm.position(i + 1) + "Der Betrag ist größer als 0.");
            }
        }
    }

    private static void taxProblems(Invoice invoice, Optional<SellerProfile> seller, Optional<Customer> customer,
            List<String> problems) {
        Set<TaxCategory> used = new LinkedHashSet<>();
        invoice.lines().forEach(line -> used.add(line.category()));
        if (used.contains(TaxCategory.NOT_TAXABLE_ABROAD) && used.size() > 1) {
            problems.add("„Nicht steuerbar“ steht allein auf einer Rechnung (EN 16931, BR-O-12).");
        }
        TaxStatus status = seller.map(SellerProfile::taxStatus).orElse(null);
        if (status == TaxStatus.KLEINUNTERNEHMER && used.stream().anyMatch(TaxCategory::chargesVat)) {
            problems.add("Als Kleinunternehmer stellen Sie keine Umsatzsteuer in Rechnung (§ 19 UStG).");
        }
        if (status == TaxStatus.REGULAR && used.contains(TaxCategory.KLEINUNTERNEHMER)) {
            problems.add("Mit Regelbesteuerung gibt es keine Positionen nach § 19 UStG.");
        }
        if (used.contains(TaxCategory.REVERSE_CHARGE_EU)
                && (seller.map(SellerProfile::vatId).isEmpty() || customer.map(Customer::vatId).isEmpty())) {
            problems.add("Reverse Charge braucht die USt-IdNr. von Ihnen und vom Kunden (§ 14a UStG).");
        }
    }

    /**
     * Returns the document of a complete draft, with "Entwurf" as number and the placeholders of its texts filled,
     * for the totals and the preview.
     *
     * @param invoice the draft
     * @return the document, empty while {@link #problems(Invoice)} is not empty
     */
    public Optional<InvoiceDocument> document(Invoice invoice) {
        if (!problems(invoice).isEmpty()) {
            return Optional.empty();
        }
        SellerProfile seller = sellers.find().orElseThrow();
        Customer customer = customers.find(invoice.customerId()).orElseThrow();
        Placeholders values = Placeholders.of(invoice, DRAFT_NUMBER);
        return Optional.of(new InvoiceDocument(DRAFT_NUMBER, invoice.issueDate(), invoice.serviceFrom(),
                invoice.serviceTo(), invoice.dueDate(), seller.toParty(), customer.toParty(), invoice.buyerReference(),
                seller.iban(), seller.bic(), values.fill(invoice.intro()), values.fill(invoice.closing()),
                values.fill(invoice.taxNote()), invoice.format(),
                invoice.lines().stream().map(DraftLine::toInvoiceLine).toList()));
    }

    private Invoice draft(long id) {
        Invoice invoice = invoices.findById(id)
                .orElseThrow(() -> new InvoiceException(List.of("Diese Rechnung gibt es nicht.")));
        if (!invoice.isDraft()) {
            throw new InvoiceException(List.of("Eine ausgestellte Rechnung ändert sich nicht mehr."));
        }
        return invoice;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
