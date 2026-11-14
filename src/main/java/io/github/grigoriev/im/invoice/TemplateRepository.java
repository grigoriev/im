package io.github.grigoriev.im.invoice;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the templates with their lines. */
interface TemplateRepository extends ListCrudRepository<InvoiceTemplate, Long> {

    /**
     * Lists the templates of a customer by name.
     *
     * @param customerId the customer
     * @return the templates
     */
    List<InvoiceTemplate> findByCustomerIdOrderByNameAsc(Long customerId);
}
