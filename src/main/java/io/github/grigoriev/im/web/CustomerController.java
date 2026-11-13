package io.github.grigoriev.im.web;

import io.github.grigoriev.im.country.CountryService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerException;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The page "Kunden": the list of customers and the form of one customer. Both roles use it. */
@Controller
@RequestMapping("/kunden")
public class CustomerController {

    static final String VIEW = "customers";

    /** The German names of the formats. */
    static final Map<InvoiceFormat, String> FORMATS = Map.of(
            InvoiceFormat.ZUGFERD_EN16931, "ZUGFeRD EN16931: PDF mit XML",
            InvoiceFormat.ZUGFERD_XRECHNUNG, "ZUGFeRD XRECHNUNG: PDF mit XML",
            InvoiceFormat.XRECHNUNG, "XRechnung: nur XML");

    private static final String SUCCESS = "success";

    private final CustomerService customers;
    private final CountryService countries;

    /**
     * Creates the controller.
     *
     * @param customers the customer service
     * @param countries the countries of the form
     */
    public CustomerController(CustomerService customers, CountryService countries) {
        this.customers = customers;
        this.countries = countries;
    }

    /**
     * Shows the list of customers.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String list(Model model) {
        return page(model, null);
    }

    /**
     * Shows an empty form for a new customer.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/neu")
    public String create(Model model) {
        return page(model, Customer.empty());
    }

    /**
     * Shows the form of a customer.
     *
     * @param id the ID
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/{id}")
    public String show(@PathVariable long id, Model model) {
        Customer customer = customers.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return page(model, customer);
    }

    /**
     * Creates a customer. On a problem the form shows again with the entered values.
     *
     * @param input the customer as entered
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the new customer, or the form with the problems
     */
    @PostMapping
    public String add(@ModelAttribute("customer") Customer input, Model model, RedirectAttributes flash) {
        try {
            Customer saved = customers.create(input);
            flash.addFlashAttribute(SUCCESS, "Kunde „" + saved.name() + "“ angelegt.");
            return "redirect:/kunden/" + saved.id();
        } catch (CustomerException e) {
            model.addAttribute("problems", e.problems());
            return page(model, input.withId(null));
        }
    }

    /**
     * Changes a customer. On a problem the form shows again with the entered values.
     *
     * @param id the ID
     * @param input the customer as entered
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the customer, or the form with the problems
     */
    @PostMapping("/{id}")
    public String change(@PathVariable long id, @ModelAttribute("customer") Customer input, Model model,
            RedirectAttributes flash) {
        try {
            customers.update(id, input);
            flash.addFlashAttribute(SUCCESS, "Änderungen gespeichert.");
            return "redirect:/kunden/" + id;
        } catch (CustomerException e) {
            model.addAttribute("problems", e.problems());
            return page(model, input.withId(id));
        }
    }

    /**
     * Removes a customer.
     *
     * @param id the ID
     * @param flash messages for the next page
     * @return a redirect to the list
     */
    @PostMapping("/{id}/loeschen")
    public String remove(@PathVariable long id, RedirectAttributes flash) {
        try {
            Customer removed = customers.delete(id);
            flash.addFlashAttribute(SUCCESS, "Kunde „" + removed.name() + "“ gelöscht.");
        } catch (CustomerException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/kunden";
    }

    /** The country stored for the customer: a new customer has none, its default comes from the list. */
    private String stored(Customer customer) {
        if (customer == null || customer.id() == null) {
            return null;
        }
        return customers.find(customer.id()).map(Customer::countryCode).orElse(null);
    }

    private String page(Model model, Customer customer) {
        model.addAttribute("customerList", customers.list());
        model.addAttribute("customer", customer);
        model.addAttribute("formats", FORMATS);
        model.addAttribute("formatOrder", InvoiceFormat.values());
        model.addAttribute("countries", countries.options(stored(customer)));
        return VIEW;
    }
}
