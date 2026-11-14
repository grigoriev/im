package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.TaxNoteService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, changes and deletes the templates of a customer (docs/PLAN.md, section 7). */
@Service
public class TemplateService {

    static final int MAX_NAME = 100;
    static final int MAX_SUBJECT = 200;
    static final int MAX_BODY = 4000;
    static final String DEFAULT_NAME = "Neue Vorlage";
    static final String DEFAULT_SUBJECT = "Rechnung {Rechnungsnummer}";
    static final String DEFAULT_BODY = """
            Sehr geehrte Damen und Herren,

            anbei erhalten Sie die Rechnung {Rechnungsnummer} über die Leistung im Zeitraum {Leistungszeitraum}.

            Mit freundlichen Grüßen""";
    static final String CHANGED_ELSEWHERE = "Die Vorlage wurde inzwischen in einem anderen Fenster geändert. "
            + "Bitte die Seite neu laden.";

    private final TemplateRepository templates;
    private final CustomerService customers;
    private final InvoiceService invoices;
    private final TaxNoteService notes;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param templates the template store
     * @param customers the customers
     * @param invoices the invoices, for the tax category of a new line
     * @param notes the default tax notes
     * @param audit the audit log
     * @param clock source of the time
     */
    public TemplateService(TemplateRepository templates, CustomerService customers, InvoiceService invoices,
            TaxNoteService notes, AuditService audit, Clock clock) {
        this.templates = templates;
        this.customers = customers;
        this.invoices = invoices;
        this.notes = notes;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Lists the templates of a customer by name.
     *
     * @param customerId the customer
     * @return the templates
     */
    public List<InvoiceTemplate> list(long customerId) {
        return templates.findByCustomerIdOrderByNameAsc(customerId);
    }

    /**
     * Returns a template of a customer.
     *
     * @param customerId the customer
     * @param id the template
     * @return the template, empty if it does not exist or belongs to another customer
     */
    public Optional<InvoiceTemplate> find(long customerId, long id) {
        return templates.findById(id).filter(template -> template.customerId() == customerId);
    }

    /**
     * Creates a template for a customer with the defaults of a new draft.
     *
     * @param customerId the customer
     * @return the saved template
     * @throws InvoiceException if the customer does not exist
     */
    @Transactional
    public InvoiceTemplate create(long customerId) {
        Customer customer = customers.find(customerId)
                .orElseThrow(() -> new InvoiceException(List.of("Diesen Kunden gibt es nicht.")));
        TaxCategory category = invoices.defaultCategory();
        InvoiceTemplate saved = templates.save(new InvoiceTemplate(null, customerId, DEFAULT_NAME, customer.format(),
                InvoiceService.DEFAULT_PAYMENT_DAYS, InvoiceService.DEFAULT_INTRO, InvoiceService.DEFAULT_CLOSING,
                notes.note(category), DEFAULT_SUBJECT, DEFAULT_BODY, List.of(TemplateLine.of(DraftLine.empty(category))),
                clock.instant(), clock.instant(), null));
        audit.add(AuditAction.TEMPLATE_CREATED, customer.name());
        return saved;
    }

    /**
     * Saves a template as entered.
     *
     * @param customerId the customer
     * @param id the template
     * @param form the entered values
     * @return the saved template
     * @throws InvoiceException with every problem, or if the template is unknown or changed elsewhere
     */
    @Transactional
    public InvoiceTemplate save(long customerId, long id, TemplateForm form) {
        InvoiceTemplate template = find(customerId, id)
                .orElseThrow(() -> new InvoiceException(List.of("Diese Vorlage gibt es nicht.")));
        if (form.getVersion() == null || !form.getVersion().equals(template.version())) {
            throw new InvoiceException(List.of(CHANGED_ELSEWHERE));
        }
        List<String> problems = new ArrayList<>();
        List<TemplateLine> lines = new ArrayList<>();
        for (int i = 0; i < form.getLines().size(); i++) {
            lines.add(TemplateLine.of(form.getLines().get(i).toLine(i + 1, problems)));
        }
        if (form.getName() == null || form.getName().isBlank()) {
            problems.add("Bitte der Vorlage einen Namen geben.");
        }
        LineForm.length(problems, form.getName(), "Der Name", MAX_NAME);
        LineForm.length(problems, form.getIntro(), "Die Einleitung", InvoiceService.MAX_TEXT);
        LineForm.length(problems, form.getClosing(), "Der Schlusstext", InvoiceService.MAX_TEXT);
        LineForm.length(problems, form.getTaxNote(), "Der Steuerhinweis", InvoiceService.MAX_TAX_NOTE);
        LineForm.length(problems, form.getEmailSubject(), "Der Betreff", MAX_SUBJECT);
        LineForm.length(problems, form.getEmailBody(), "Der Text der E-Mail", MAX_BODY);
        if (form.getPaymentDays() < 0 || form.getPaymentDays() > InvoiceService.MAX_PAYMENT_DAYS) {
            problems.add("Das Zahlungsziel liegt zwischen 0 und " + InvoiceService.MAX_PAYMENT_DAYS + " Tagen.");
        }
        if (form.getFormat() == null) {
            problems.add("Bitte das E-Rechnungsformat wählen.");
        }
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        try {
            return templates.save(new InvoiceTemplate(template.id(), customerId, form.getName().strip(),
                    form.getFormat(), form.getPaymentDays(), text(form.getIntro()), text(form.getClosing()),
                    text(form.getTaxNote()), text(form.getEmailSubject()), text(form.getEmailBody()), lines,
                    template.createdAt(), clock.instant(), template.version()));
        } catch (OptimisticLockingFailureException _) {
            throw new InvoiceException(List.of(CHANGED_ELSEWHERE));
        }
    }

    /**
     * Deletes a template. Drafts made from it stay as they are.
     *
     * @param customerId the customer
     * @param id the template
     * @return the deleted template
     * @throws InvoiceException if the template does not exist
     */
    @Transactional
    public InvoiceTemplate delete(long customerId, long id) {
        InvoiceTemplate template = find(customerId, id)
                .orElseThrow(() -> new InvoiceException(List.of("Diese Vorlage gibt es nicht.")));
        templates.deleteById(id);
        audit.add(AuditAction.TEMPLATE_DELETED, template.name());
        return template;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
