package io.github.grigoriev.im.web;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.TemplateForm;
import io.github.grigoriev.im.invoice.TemplateService;
import java.util.List;
import org.springframework.http.HttpStatus;
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

/** The templates of a customer: create, edit, delete. Both roles use them. */
@Controller
@RequestMapping("/kunden/{customerId}/vorlagen")
public class TemplateController {

    static final String VIEW = "invoice-templates/edit";

    private static final String SUCCESS = "success";
    private static final String CUSTOMER = "redirect:/kunden/";

    private final TemplateService templates;
    private final CustomerService customers;
    private final InvoiceService invoices;

    /**
     * Creates the controller.
     *
     * @param templates the template service
     * @param customers the customers
     * @param invoices the invoices, for the tax category of a new line
     */
    public TemplateController(TemplateService templates, CustomerService customers, InvoiceService invoices) {
        this.templates = templates;
        this.customers = customers;
        this.invoices = invoices;
    }

    /**
     * Creates a template for the customer.
     *
     * @param customerId the customer
     * @param flash messages for the next page
     * @return a redirect to the editor, or to the customer with the problem
     */
    @PostMapping
    public String create(@PathVariable long customerId, RedirectAttributes flash) {
        try {
            return CUSTOMER + customerId + "/vorlagen/" + templates.create(customerId).id();
        } catch (InvoiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/kunden";
        }
    }

    /**
     * Shows the editor of a template.
     *
     * @param customerId the customer
     * @param id the template
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/{id}")
    public String edit(@PathVariable long customerId, @PathVariable long id, Model model) {
        InvoiceTemplate template = template(customerId, id);
        return page(model, customerId, template, TemplateForm.of(template));
    }

    /**
     * Saves a template. "position" adds a line, "entfernen" removes one; both save the rest of the form too.
     *
     * @param customerId the customer
     * @param id the template
     * @param form the entered values
     * @param binding problems of the binding, for example an empty payment term
     * @param aktion "speichern" or "position"
     * @param entfernen the index of the line to remove, if any
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the editor, or the editor with the problems
     */
    @PostMapping("/{id}")
    public String save(@PathVariable long customerId, @PathVariable long id, @ModelAttribute("form") TemplateForm form,
            BindingResult binding, @RequestParam(required = false) String aktion,
            @RequestParam(required = false) Integer entfernen, Model model, RedirectAttributes flash) {
        InvoiceTemplate template = template(customerId, id);
        if (binding.hasErrors()) {
            model.addAttribute("formProblems", List.of("Bitte gültige Daten eingeben, zum Beispiel ein Zahlungsziel."));
            return page(model, customerId, template, form);
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
            templates.save(customerId, id, form);
        } catch (InvoiceException e) {
            model.addAttribute("formProblems", e.problems());
            return page(model, customerId, template, form);
        }
        if ("speichern".equals(aktion)) {
            flash.addFlashAttribute(SUCCESS, "Vorlage gespeichert.");
        }
        return CUSTOMER + customerId + "/vorlagen/" + id;
    }

    /**
     * Deletes a template.
     *
     * @param customerId the customer
     * @param id the template
     * @param flash messages for the next page
     * @return a redirect to the customer
     */
    @PostMapping("/{id}/loeschen")
    public String delete(@PathVariable long customerId, @PathVariable long id, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute(SUCCESS, "Vorlage „" + templates.delete(customerId, id).name() + "“ gelöscht.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return CUSTOMER + customerId;
    }

    private InvoiceTemplate template(long customerId, long id) {
        return templates.find(customerId, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String page(Model model, long customerId, InvoiceTemplate template, TemplateForm form) {
        model.addAttribute("template", template);
        model.addAttribute("form", form);
        model.addAttribute("customer", customers.find(customerId).map(Customer::name).orElse("–"));
        model.addAttribute("customerId", customerId);
        model.addAttribute("formats", CustomerController.FORMATS);
        model.addAttribute("formatOrder", InvoiceFormat.values());
        model.addAttribute("categories", InvoiceController.CATEGORIES);
        model.addAttribute("categoryOrder", TaxCategory.values());
        model.addAttribute("placeholders", List.of("{Rechnungsnummer}", "{Rechnungsdatum}", "{Leistungszeitraum}",
                "{Zahlungsziel}"));
        return VIEW;
    }
}
