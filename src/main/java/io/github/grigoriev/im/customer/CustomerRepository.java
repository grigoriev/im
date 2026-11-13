package io.github.grigoriev.im.customer;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the customers. */
interface CustomerRepository extends ListCrudRepository<Customer, Long> {

    /**
     * Lists all customers by name.
     *
     * @return the customers
     */
    List<Customer> findAllByOrderByNameAsc();
}
