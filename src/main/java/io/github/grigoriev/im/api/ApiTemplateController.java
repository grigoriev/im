package io.github.grigoriev.im.api;

import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.invoice.TemplateForm;
import io.github.grigoriev.im.invoice.TemplateService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The templates of a customer in the API. A template reads as the form of its editor, with the amounts as Germans
 * type them, and a change sends that form back with its {@code version}. Deleting stays in the form.
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/templates")
public class ApiTemplateController {

    static final String MISSING = "Diese Vorlage gibt es nicht.";

    private final TemplateService templates;
    private final CustomerService customers;

    /**
     * Creates the controller.
     *
     * @param templates the templates
     * @param customers the customers
     */
    public ApiTemplateController(TemplateService templates, CustomerService customers) {
        this.templates = templates;
        this.customers = customers;
    }

    /**
     * Lists the templates of a customer by name.
     *
     * @param customerId the customer
     * @return the templates
     * @throws NotFoundException if the customer does not exist
     */
    @GetMapping
    public List<InvoiceTemplate> list(@PathVariable long customerId) {
        if (customers.find(customerId).isEmpty()) {
            throw new NotFoundException(ApiCustomerController.MISSING);
        }
        return templates.list(customerId);
    }

    /**
     * Returns a template as the form of its editor.
     *
     * @param customerId the customer
     * @param id the template
     * @return the form
     * @throws NotFoundException if the template does not exist
     */
    @GetMapping("/{id}")
    public TemplateForm get(@PathVariable long customerId, @PathVariable long id) {
        return templates.find(customerId, id).map(TemplateForm::of).orElseThrow(() -> new NotFoundException(MISSING));
    }

    /**
     * Creates a template with the defaults of the customer, as the button in the form does.
     *
     * @param customerId the customer
     * @return 201 with the template and its URL
     */
    @PostMapping
    public ResponseEntity<InvoiceTemplate> create(@PathVariable long customerId) {
        InvoiceTemplate created = templates.create(customerId);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customerId + "/templates/" + created.id()))
                .body(created);
    }

    /**
     * Saves a template from its form, with the checks of the editor.
     *
     * @param customerId the customer
     * @param id the template
     * @param form the form with the {@code version} it was read with
     * @return the saved template as a form
     */
    @PutMapping("/{id}")
    public TemplateForm save(@PathVariable long customerId, @PathVariable long id, @RequestBody TemplateForm form) {
        return TemplateForm.of(templates.save(customerId, id, form));
    }
}
