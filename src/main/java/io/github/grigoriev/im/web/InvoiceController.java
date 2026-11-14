package io.github.grigoriev.im.web;

import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.StoredDocument;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

/**
 * The page "Rechnungen": the list with its filters, a new draft for a customer, the editor of a draft, the issue, and
 * the page of an issued invoice ({@link IssuedInvoicePage}). Both roles use it.
 */
@Controller
@RequestMapping("/rechnungen")
public class InvoiceController {

    static final String LIST = "invoices/list";
    static final String EDIT = "invoices/edit";
    static final String ISSUED = "invoices/issued";

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
    private static final String TO_INVOICE = "redirect:/rechnungen/";

    /**
     * A line of the list.
     *
     * @param invoice the invoice
     * @param customer the customer's name; the frozen buyer of an issued invoice
     * @param total the gross total, {@code null} while the draft is incomplete; the frozen one of an issued invoice
     */
    public record Row(Invoice invoice, String customer, BigDecimal total) {
    }

    private final InvoiceService invoices;
    private final IssueService issues;
    private final DocumentStore documents;
    private final CustomerService customers;
    private final InvoicePdfRenderer pdf;
    private final IssuedInvoicePage issuedPage;

    /**
     * Creates the controller.
     *
     * @param invoices the invoice service
     * @param issues issues a draft
     * @param documents the documents and snapshots of issued invoices
     * @param customers the customers for a new draft
     * @param pdf renders the preview
     * @param issuedPage fills the page of an issued invoice
     */
    public InvoiceController(InvoiceService invoices, IssueService issues, DocumentStore documents,
            CustomerService customers, InvoicePdfRenderer pdf, IssuedInvoicePage issuedPage) {
        this.invoices = invoices;
        this.issues = issues;
        this.documents = documents;
        this.customers = customers;
        this.pdf = pdf;
        this.issuedPage = issuedPage;
    }

    /**
     * Shows the list of invoices, filtered by the year of the invoice date, the status and a text.
     *
     * @param jahr the year, {@code null} for all
     * @param status the status, {@code null} for all
     * @param suche a part of the customer's name or of the number, case ignored; blank for all
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String list(@RequestParam(required = false) Integer jahr, @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) String suche, Model model) {
        List<Row> all = invoices.list().stream().map(invoice -> {
            Optional<InvoiceDocument> document = document(invoice);
            return new Row(invoice, customerName(invoice, document),
                    document.map(InvoiceDocument::total).orElse(null));
        }).toList();
        String text = suche == null ? "" : suche.strip().toLowerCase(Locale.ROOT);
        model.addAttribute("rows", all.stream().filter(row -> matches(row, jahr, status, text)).toList());
        model.addAttribute("any", !all.isEmpty());
        model.addAttribute("years", all.stream().map(row -> row.invoice().issueDate().getYear()).distinct()
                .sorted(Comparator.reverseOrder()).toList());
        model.addAttribute("statuses", InvoiceStatus.values());
        model.addAttribute("jahr", jahr);
        model.addAttribute("status", status);
        model.addAttribute("suche", suche == null ? "" : suche.strip());
        model.addAttribute("customers", customers.list());
        return LIST;
    }

    /** Tells whether a row passes the filters of the list; a {@code null} or empty filter passes every row. */
    static boolean matches(Row row, Integer year, InvoiceStatus status, String text) {
        Invoice invoice = row.invoice();
        return (year == null || invoice.issueDate().getYear() == year)
                && (status == null || invoice.status() == status)
                && (text.isEmpty() || row.customer().toLowerCase(Locale.ROOT).contains(text)
                        || invoice.number() != null && invoice.number().toLowerCase(Locale.ROOT).contains(text));
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
            return TO_INVOICE + invoices.createDraft(customerId, templateId).id();
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
        return TO_INVOICE + id;
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
        if (!invoice.isDraft()) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/rechnungen/" + id + "/dokumente/pdf"))
                    .build();
        }
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
     * Issues a draft: its number, its frozen snapshot and its validated documents.
     *
     * @param id the ID
     * @param version the version of the draft the user saw
     * @param flash messages for the next page
     * @return a redirect to the invoice
     */
    @PostMapping("/{id}/ausstellen")
    public String issue(@PathVariable long id, @RequestParam(required = false) Long version, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute(SUCCESS, "Rechnung " + issues.issue(id, version) + " ausgestellt.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return TO_INVOICE + id;
    }

    /**
     * Downloads a document of an issued invoice, byte for byte as it was issued.
     *
     * @param id the ID
     * @param kind "pdf" or "xml"
     * @return the file
     */
    @GetMapping("/{id}/dokumente/{kind}")
    public ResponseEntity<byte[]> download(@PathVariable long id, @PathVariable String kind) {
        DocumentKind documentKind = switch (kind) {
            case "pdf" -> DocumentKind.PDF;
            case "xml" -> DocumentKind.XML;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        StoredDocument document = documents.find(id, documentKind)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(documentKind.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.filename()).build().toString())
                .body(document.content());
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
        Optional<InvoiceDocument> document = document(invoice);
        model.addAttribute("customer", customerName(invoice, document));
        model.addAttribute("document", document.orElse(null));
        model.addAttribute("formats", CustomerController.FORMATS);
        if (!invoice.isDraft()) {
            issuedPage.fill(model, invoice);
            return ISSUED;
        }
        model.addAttribute("form", form);
        model.addAttribute("problems", invoices.problems(invoice));
        model.addAttribute("formatOrder", InvoiceFormat.values());
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("categoryOrder", TaxCategory.values());
        return EDIT;
    }

    /** The current customer of a draft, or the frozen buyer of an issued invoice. */
    private String customerName(Invoice invoice, Optional<InvoiceDocument> document) {
        return invoice.isDraft() ? invoices.customerName(invoice) : document.orElseThrow().buyer().name();
    }

    /** The document of a draft from the current data, or the frozen snapshot of an issued invoice. */
    private Optional<InvoiceDocument> document(Invoice invoice) {
        return invoice.isDraft() ? invoices.document(invoice) : Optional.of(documents.restore(invoice.snapshot()));
    }
}
