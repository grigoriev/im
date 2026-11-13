package io.github.grigoriev.im.customer;

import static io.github.grigoriev.im.validation.Fields.compact;
import static io.github.grigoriev.im.validation.Fields.checkCountry;
import static io.github.grigoriev.im.validation.Fields.checkEmail;
import static io.github.grigoriev.im.validation.Fields.optional;
import static io.github.grigoriev.im.validation.Fields.required;
import static io.github.grigoriev.im.validation.Fields.text;
import static io.github.grigoriev.im.validation.Fields.upper;
import static io.github.grigoriev.im.validation.Fields.checkVatId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists, creates, changes and removes customers. Both XRechnung formats need a buyer reference (BR-DE-15)
 * and the e-mail address of the buyer (BT-49).
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    /**
     * Creates the service.
     *
     * @param customers the customer store
     */
    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * Lists all customers by name.
     *
     * @return the customers
     */
    public List<Customer> list() {
        return customers.findAllByOrderByNameAsc();
    }

    /**
     * Returns a customer.
     *
     * @param id the ID
     * @return the customer, if it exists
     */
    public Optional<Customer> find(long id) {
        return customers.findById(id);
    }

    /**
     * Creates a customer.
     *
     * @param input the customer as entered; its ID is ignored
     * @return the saved customer with its ID
     * @throws CustomerException with every problem found
     */
    @Transactional
    public Customer create(Customer input) {
        return customers.save(checked(input).withId(null));
    }

    /**
     * Changes a customer.
     *
     * @param id the ID of the customer
     * @param input the customer as entered; its ID is ignored
     * @return the saved customer
     * @throws CustomerException if the customer does not exist, or with every problem found
     */
    @Transactional
    public Customer update(long id, Customer input) {
        Customer customer = checked(input).withId(id);
        if (!customers.existsById(id)) {
            throw new CustomerException(List.of("Diesen Kunden gibt es nicht."));
        }
        return customers.save(customer);
    }

    /**
     * Removes a customer.
     *
     * @param id the ID of the customer
     * @return the removed customer
     * @throws CustomerException if the customer does not exist
     */
    @Transactional
    public Customer delete(long id) {
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new CustomerException(List.of("Diesen Kunden gibt es nicht.")));
        customers.deleteById(id);
        return customer;
    }

    static Customer normalize(Customer input) {
        return new Customer(input.id(), text(input.name()), text(input.street()), text(input.postcode()),
                text(input.city()), upper(input.countryCode()), text(input.contactName()), text(input.email()),
                compact(input.vatId()), text(input.buyerReference()), input.format());
    }

    static List<String> check(Customer customer) {
        List<String> problems = new ArrayList<>();
        required(problems, customer.name(), "Name", 200);
        required(problems, customer.street(), "Straße", 200);
        required(problems, customer.postcode(), "PLZ", 16);
        required(problems, customer.city(), "Ort", 100);
        checkCountry(problems, customer.countryCode());
        optional(problems, customer.contactName(), "Ansprechpartner", 200);
        if (customer.email() != null) {
            checkEmail(problems, customer.email());
        }
        checkVatId(problems, customer.vatId());
        optional(problems, customer.buyerReference(), "Käuferreferenz", 200);
        if (customer.format() == null) {
            problems.add("Bitte das E-Rechnungsformat wählen.");
        } else if (customer.format().followsXRechnung()) {
            if (customer.buyerReference() == null) {
                problems.add("Die XRechnung braucht eine Käuferreferenz, zum Beispiel die Leitweg-ID (BR-DE-15).");
            }
            if (customer.email() == null) {
                problems.add("Die XRechnung braucht die E-Mail-Adresse des Kunden.");
            }
        }
        return problems;
    }

    private static Customer checked(Customer input) {
        Customer customer = normalize(input);
        List<String> problems = check(customer);
        if (!problems.isEmpty()) {
            throw new CustomerException(problems);
        }
        return customer;
    }
}
