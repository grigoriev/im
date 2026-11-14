package io.github.grigoriev.im.web;

import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.LineForm;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.net.URI;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The page "Rechnungen": the list, a new draft for a customer, and the editor of a draft. Both roles use it. */
@Controller
@RequestMapping("/rechnungen")
public class InvoiceController {

    static final String LIST = "invoices/list";
    static final String EDIT = "invoices/edit";

    /** The names of the tax categories in the editor. */
    static final Map<TaxCategory, String> CATEGORIES = Map.of(
            TaxCategory.KLEINUNTERNEHMER, "§ 19 UStG, Kleinunternehmer",
            TaxCategory.EXEMPT_4_21, "§ 4 Nr. 21 UStG, steuerfrei",
            TaxCategory.REVERSE_CHARGE_EU, "Reverse Charge, EU",
            TaxCategory.NOT_TAXABLE_ABROAD, "Nicht steuerbar, Ausland",
            TaxCategory.STANDARD_19, "19 % Umsatzsteuer",
            TaxCategory.STANDARD_7, "7 % Umsatzsteuer");

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";
    private static final String BACK = "redirect:/rechnungen";

    /**
     * A line of the list.
     *
     * @param invoice the invoice
     * @param customer the customer's name
     * @param total the gross total, {@code null} while the draft is incomplete
     */
    public record Row(Invoice invoice, String customer, BigDecimal total) {
    }

    private final InvoiceService invoices;
    private final CustomerService customers;
    private final InvoicePdfRenderer pdf;

    /**
     * Creates the controller.
     *
     * @param invoices the invoice service
     * @param customers the customers for a new draft
     * @param pdf renders the preview
     */
    public InvoiceController(InvoiceService invoices, CustomerService customers, InvoicePdfRenderer pdf) {
        this.invoices = invoices;
        this.customers = customers;
        this.pdf = pdf;
    }

    /**
     * Shows the list of invoices.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String list(Model model) {
        List<Row> rows = invoices.list().stream().map(invoice -> new Row(invoice, invoices.customerName(invoice),
                invoices.document(invoice).map(InvoiceDocument::total).orElse(null))).toList();
        model.addAttribute("rows", rows);
        model.addAttribute("customers", customers.list());
        return LIST;
    }

    /**
     * Creates a draft for a customer, from one of the customer's templates if given.
     *
     * @param customerId the customer
     * @param templateId the template, if any
     * @param flash messages for the next page
     * @return a redirect to the editor, or to the list with the problem
     */
    @PostMapping
    public String create(@RequestParam long customerId, @RequestParam(required = false) Long templateId,
            RedirectAttributes flash) {
        try {
            return "redirect:/rechnungen/" + invoices.createDraft(customerId, templateId).id();
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
            return BACK;
        }
    }

    /**
     * Shows the editor of an invoice.
     *
     * @param id the ID
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/{id}")
    public String edit(@PathVariable long id, Model model) {
        Invoice invoice = invoices.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return page(model, invoice, DraftForm.of(invoice));
    }

    /**
     * Saves a draft. "position" adds a line, "entfernen" removes one; both save the rest of the form too.
     *
     * @param id the ID
     * @param form the entered values
     * @param binding problems of the binding, for example a broken date
     * @param aktion "speichern" or "position"
     * @param entfernen the index of the line to remove, if any
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the editor, or the editor with the problems
     */
    @PostMapping("/{id}")
    public String save(@PathVariable long id, @ModelAttribute("form") DraftForm form, BindingResult binding,
            @RequestParam(required = false) String aktion, @RequestParam(required = false) Integer entfernen,
            Model model, RedirectAttributes flash) {
        Invoice invoice = invoices.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (binding.hasErrors()) {
            model.addAttribute("formProblems", List.of("Bitte gültige Daten eingeben, zum Beispiel ein Datum."));
            return page(model, invoice, form);
        }
        if (entfernen != null && entfernen >= 0 && entfernen < form.getLines().size()) {
            form.getLines().remove(entfernen.intValue());
        }
        if ("position".equals(aktion)) {
            LineForm line = new LineForm();
            line.setCategory(invoices.defaultCategory());
            form.getLines().add(line);
        }
        try {
            invoices.saveDraft(id, form);
        } catch (InvoiceException e) {
            model.addAttribute("formProblems", e.problems());
            return page(model, invoice, form);
        }
        if ("speichern".equals(aktion)) {
            flash.addFlashAttribute(SUCCESS, "Entwurf gespeichert.");
        }
        return "redirect:/rechnungen/" + id;
    }

    /**
     * Shows the PDF of a complete draft as it will be issued, with "Entwurf" as number.
     *
     * @param id the ID
     * @param flash messages for the next page
     * @return the PDF, or a redirect to the editor while the draft is incomplete
     */
    @GetMapping("/{id}/vorschau")
    public ResponseEntity<byte[]> preview(@PathVariable long id, RedirectAttributes flash) {
        Invoice invoice = invoices.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return invoices.document(invoice)
                .map(document -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename("Rechnungsentwurf.pdf").build().toString())
                        .body(pdf.render(document)))
                .orElseGet(() -> {
                    flash.addFlashAttribute(ERROR, "Die Vorschau braucht einen vollständigen Entwurf.");
                    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/rechnungen/" + id)).build();
                });
    }

    /**
     * Deletes a draft.
     *
     * @param id the ID
     * @param flash messages for the next page
     * @return a redirect to the list
     */
    @PostMapping("/{id}/loeschen")
    public String delete(@PathVariable long id, RedirectAttributes flash) {
        try {
            invoices.deleteDraft(id);
            flash.addFlashAttribute(SUCCESS, "Entwurf gelöscht.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }

    private String page(Model model, Invoice invoice, DraftForm form) {
        model.addAttribute("invoice", invoice);
        model.addAttribute("form", form);
        model.addAttribute("customer", invoices.customerName(invoice));
        model.addAttribute("problems", invoices.problems(invoice));
        model.addAttribute("document", invoices.document(invoice).orElse(null));
        model.addAttribute("formats", CustomerController.FORMATS);
        model.addAttribute("formatOrder", InvoiceFormat.values());
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("categoryOrder", TaxCategory.values());
        return EDIT;
    }
}
