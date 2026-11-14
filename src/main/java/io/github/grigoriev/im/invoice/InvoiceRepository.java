package io.github.grigoriev.im.invoice;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the invoices with their lines. */
interface InvoiceRepository extends ListCrudRepository<Invoice, Long> {

    /**
     * Lists all invoices, the newest first.
     *
     * @return the invoices
     */
    List<Invoice> findAllByOrderByIdDesc();
}
