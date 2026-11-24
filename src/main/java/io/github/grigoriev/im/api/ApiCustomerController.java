package io.github.grigoriev.im.api;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
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
 * The customers in the API: list, read, create and change, with the checks of the form. Deleting stays in the form.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class ApiCustomerController {

    static final String MISSING = "Diesen Kunden gibt es nicht.";

    private final CustomerService customers;

    /**
     * Creates the controller.
     *
     * @param customers the customers
     */
    public ApiCustomerController(CustomerService customers) {
        this.customers = customers;
    }

    /**
     * Lists all customers by name and contact person.
     *
     * @return the customers
     */
    @GetMapping
    public List<Customer> list() {
        return customers.list();
    }

    /**
     * Returns a customer.
     *
     * @param id the ID
     * @return the customer
     * @throws NotFoundException if it does not exist
     */
    @GetMapping("/{id}")
    public Customer get(@PathVariable long id) {
        return customers.find(id).orElseThrow(() -> new NotFoundException(MISSING));
    }

    /**
     * Creates a customer. Its ID in the body is ignored.
     *
     * @param input the customer
     * @return 201 with the saved customer and its URL
     */
    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Customer input) {
        Customer saved = customers.create(input);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + saved.id())).body(saved);
    }

    /**
     * Changes a customer with every field of the body. Its ID in the body is ignored.
     *
     * @param id the ID
     * @param input the customer
     * @return the saved customer
     * @throws NotFoundException if it does not exist
     */
    @PutMapping("/{id}")
    public Customer update(@PathVariable long id, @RequestBody Customer input) {
        get(id);
        return customers.update(id, input);
    }
}
