package io.github.grigoriev.im.invoice;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the dispatches. Entries are only added. */
interface DispatchRepository extends ListCrudRepository<Dispatch, Long> {

    /**
     * Returns the dispatches of an invoice.
     *
     * @param invoiceId the invoice
     * @return its dispatches, oldest first
     */
    List<Dispatch> findByInvoiceIdOrderBySentOnAscIdAsc(long invoiceId);

    /**
     * Tells whether an invoice went out at least once.
     *
     * @param invoiceId the invoice
     * @return {@code true} if it has a dispatch
     */
    boolean existsByInvoiceId(long invoiceId);
}
