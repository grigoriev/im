package io.github.grigoriev.im.web;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import io.github.grigoriev.im.einvoice.Party;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.ExternalInvoiceService;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceOriginal;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.OriginalStore;
import io.github.grigoriev.im.invoice.StoredDocument;
import io.github.grigoriev.im.threshold.ThresholdService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The page "Rechnungen": the list with its filters, a new draft for a customer, the editor of a draft, the issue, the
 * registration of an external invoice with its original files, and the page of an issued invoice
 * ({@link IssuedInvoicePage}). Both roles use it.
 */
@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    static final String LIST = "invoices/list";
    static final String EDIT = "invoices/edit";
    static final String ISSUED = "invoices/issued";
    private static final String INVOICE_PATH = "/invoices";

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
    private static final String BACK = "redirect:/invoices";
    private static final String TO_INVOICE = "redirect:/invoices/";

    /**
     * A line of the list.
     *
     * @param invoice the invoice
     * @param customer the customer's name; the frozen buyer of an issued invoice
     * @param total the gross total, {@code null} while the draft is incomplete; the frozen one of an issued invoice
     * @param cancellation whether it is a Stornorechnung
     */
    public record Row(Invoice invoice, String customer, BigDecimal total, boolean cancellation) {
    }

    private final InvoiceService invoices;
    private final IssueService issues;
    private final ExternalInvoiceService externals;
    private final DocumentStore documents;
    private final OriginalStore originals;
    private final CustomerService customers;
    private final InvoicePdfRenderer pdf;
    private final IssuedInvoicePage issuedPage;
    private final ThresholdService threshold;

    /**
     * Creates the controller.
     *
     * @param invoices the invoice service
     * @param issues issues a draft
     * @param externals registers an external invoice
     * @param documents the documents and snapshots of issued invoices
     * @param originals the original files of external invoices
     * @param customers the customers for a new draft
     * @param pdf renders the preview
     * @param issuedPage fills the page of an issued invoice
     * @param threshold warns when a draft would cross the §19 limit
     */
    @SuppressWarnings("java:S107") // the services of the pages of an invoice
    public InvoiceController(InvoiceService invoices, IssueService issues, ExternalInvoiceService externals,
            DocumentStore documents, OriginalStore originals, CustomerService customers, InvoicePdfRenderer pdf,
            IssuedInvoicePage issuedPage, ThresholdService threshold) {
        this.invoices = invoices;
        this.issues = issues;
        this.externals = externals;
        this.documents = documents;
        this.originals = originals;
        this.customers = customers;
        this.pdf = pdf;
        this.issuedPage = issuedPage;
        this.threshold = threshold;
    }

    /**
     * Shows the list of invoices, filtered by the year of the invoice date, the status and a text.
     *
     * @param year the year, {@code null} for all
     * @param status the status, {@code null} for all
     * @param q a part of the customer's name or of the number, case ignored; blank for all
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String list(@RequestParam(required = false) Integer year, @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) String q, Model model) {
        List<Row> all = invoices.list().stream().map(invoice -> {
            Optional<InvoiceDocument> document = document(invoice);
            return new Row(invoice, customerName(invoice, document),
                    document.map(InvoiceDocument::total).orElse(null), invoices.cancelled(invoice).isPresent());
        }).toList();
        String text = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        model.addAttribute("rows", all.stream().filter(row -> matches(row, year, status, text)).toList());
        model.addAttribute("any", !all.isEmpty());
        model.addAttribute("years", all.stream().map(row -> row.invoice().issueDate().getYear()).distinct()
                .sorted(Comparator.reverseOrder()).toList());
        model.addAttribute("statuses", InvoiceStatus.values());
        model.addAttribute("year", year);
        model.addAttribute("status", status);
        model.addAttribute("q", q == null ? "" : q.strip());
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
     * Saves a draft. "add-line" adds a line, "remove" removes one; both save the rest of the form too.
     *
     * @param id the ID
     * @param form the entered values
     * @param binding problems of the binding, for example a broken date
     * @param action "save" or "add-line"
     * @param remove the index of the line to remove, if any
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the editor, or the editor with the problems
     */
    @PostMapping("/{id}")
    public String save(@PathVariable long id, @ModelAttribute("form") DraftForm form, BindingResult binding,
            @RequestParam(required = false) String action, @RequestParam(required = false) Integer remove,
            Model model, RedirectAttributes flash) {
        Invoice invoice = invoices.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (binding.hasErrors()) {
            model.addAttribute("formProblems", List.of("Bitte gültige Daten eingeben, zum Beispiel ein Datum."));
            return page(model, invoice, form);
        }
        if (remove != null && remove >= 0 && remove < form.getLines().size()) {
            form.getLines().remove(remove.intValue());
        }
        if ("add-line".equals(action)) {
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
        if ("save".equals(action)) {
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
    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable long id, RedirectAttributes flash) {
        Invoice invoice = invoices.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!invoice.isDraft()) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(INVOICE_PATH + "/" + id + "/documents/pdf"))
                    .build();
        }
        if (invoice.format().isExternal()) {
            flash.addFlashAttribute(ERROR, "Eine externe Rechnung hat keine Vorschau. Ihre Datei ist die Rechnung.");
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(INVOICE_PATH + "/" + id)).build();
        }
        return invoices.document(invoice)
                .map(document -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename("Rechnungsentwurf.pdf").build().toString())
                        .body(pdf.render(document)))
                .orElseGet(() -> {
                    flash.addFlashAttribute(ERROR, "Die Vorschau braucht einen vollständigen Entwurf.");
                    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(INVOICE_PATH + "/" + id)).build();
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
    @PostMapping("/{id}/issue")
    public String issue(@PathVariable long id, @RequestParam(required = false) Long version, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute(SUCCESS, "Rechnung " + issues.issue(id, version) + " ausgestellt.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return TO_INVOICE + id;
    }

    /**
     * Registers an external invoice: the number of its file, checked against the next number, and its original file.
     *
     * @param id the ID of the draft
     * @param version the version of the draft the user saw
     * @param number the number printed on the file
     * @param file the original file
     * @param flash messages for the next page
     * @return a redirect to the invoice
     * @throws IOException if the upload cannot be read
     */
    @PostMapping("/{id}/register")
    public String register(@PathVariable long id, @RequestParam(required = false) Long version,
            @RequestParam(required = false) String number, @RequestParam(required = false) MultipartFile file,
            RedirectAttributes flash) throws IOException {
        try {
            flash.addFlashAttribute(SUCCESS, "Externe Rechnung " + externals.register(id, version, number, name(file),
                    bytes(file)) + " erfasst.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return TO_INVOICE + id;
    }

    /**
     * Adds a further original file to a registered external invoice.
     *
     * @param id the ID
     * @param file the file
     * @param flash messages for the next page
     * @return a redirect to the invoice
     * @throws IOException if the upload cannot be read
     */
    @PostMapping("/{id}/originals")
    public String addOriginal(@PathVariable long id, @RequestParam(required = false) MultipartFile file,
            RedirectAttributes flash) throws IOException {
        try {
            InvoiceOriginal added = externals.addOriginal(id, name(file), bytes(file));
            flash.addFlashAttribute(SUCCESS, "Datei „" + added.filename() + "“ hinzugefügt.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return TO_INVOICE + id;
    }

    /**
     * Downloads an original file of an external invoice, byte for byte as it was uploaded.
     *
     * @param id the ID of the invoice
     * @param originalId the ID of the file
     * @return the file; a redirect to the invoice, which shows the check, if the file is broken or changed
     */
    @GetMapping("/{id}/originals/{originalId}")
    public ResponseEntity<byte[]> downloadOriginal(@PathVariable long id, @PathVariable long originalId) {
        InvoiceOriginal original;
        try {
            original = originals.find(id, originalId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (InvoiceException _) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(INVOICE_PATH + "/" + id)).build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(original.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(original.filename(), StandardCharsets.UTF_8).build().toString())
                .body(original.content());
    }

    private static String name(MultipartFile file) {
        return file == null ? null : file.getOriginalFilename();
    }

    /** Reads the upload, unless it is too large: the upload limit of a form allows more than a file may have. */
    private static byte[] bytes(MultipartFile file) throws IOException {
        if (file == null) {
            return new byte[0];
        }
        if (file.getSize() > DocumentService.MAX_BYTES) {
            throw new InvoiceException(List.of(DocumentService.TOO_LARGE));
        }
        return file.getBytes();
    }

    /**
     * Creates the Stornorechnung of an issued invoice as a draft.
     *
     * @param id the invoice to cancel
     * @param flash messages for the next page
     * @return a redirect to the draft, or back to the invoice with the problem
     */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable long id, RedirectAttributes flash) {
        try {
            long draft = invoices.createCancellation(id).id();
            flash.addFlashAttribute(SUCCESS, "Stornorechnung als Entwurf angelegt. Die Nummer folgt beim Ausstellen.");
            return TO_INVOICE + draft;
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
            return TO_INVOICE + id;
        }
    }

    /**
     * Downloads a document of an issued invoice, byte for byte as it was issued.
     *
     * @param id the ID
     * @param kind "pdf" or "xml"
     * @return the file; a redirect to the invoice, which shows the check, if the file is broken or changed
     */
    @GetMapping("/{id}/documents/{kind}")
    public ResponseEntity<byte[]> download(@PathVariable long id, @PathVariable String kind) {
        DocumentKind documentKind = switch (kind) {
            case "pdf" -> DocumentKind.PDF;
            case "xml" -> DocumentKind.XML;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        StoredDocument document;
        try {
            document = documents.find(id, documentKind)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (InvoiceException _) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(INVOICE_PATH + "/" + id)).build();
        }
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
    @PostMapping("/{id}/delete")
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
        Optional<Invoice> cancelled = invoices.cancelled(invoice);
        model.addAttribute("cancelled", cancelled.orElse(null));
        // The disabled format select of a Stornorechnung posts nothing: after a problem, show its own format.
        cancelled.ifPresent(original -> form.setFormat(invoice.format()));
        if (!invoice.isDraft()) {
            issuedPage.fill(model, invoice);
            return ISSUED;
        }
        model.addAttribute("form", form);
        model.addAttribute("problems", invoices.problems(invoice));
        if (invoice.format().isExternal()) {
            model.addAttribute("nextNumber", externals.nextNumber(invoice));
        }
        // A Stornorechnung adds no revenue: it takes no payments.
        model.addAttribute("overLimit", cancelled.isEmpty() && document.isPresent()
                && threshold.wouldExceed(document.get()));
        model.addAttribute("formatOrder", InvoiceFormat.values());
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("categoryOrder", TaxCategory.values());
        return EDIT;
    }

    /** The current customer of a draft, or the frozen buyer of an issued invoice. */
    private String customerName(Invoice invoice, Optional<InvoiceDocument> document) {
        if (invoice.isDraft()) {
            return invoices.customerName(invoice);
        }
        Party buyer = document.orElseThrow().buyer();
        return Customer.label(buyer.name(), buyer.contactName());
    }

    /** The document of a draft from the current data, or the frozen snapshot of an issued invoice. */
    private Optional<InvoiceDocument> document(Invoice invoice) {
        return invoice.isDraft() ? invoices.document(invoice) : Optional.of(documents.restore(invoice.snapshot()));
    }
}
