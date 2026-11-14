package io.github.grigoriev.im.invoice;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the payments. Entries are only added. */
interface PaymentRepository extends ListCrudRepository<Payment, Long> {

    /**
     * Returns the payments of an invoice.
     *
     * @param invoiceId the invoice
     * @return its payments, oldest first
     */
    List<Payment> findByInvoiceIdOrderByPaidOnAscIdAsc(long invoiceId);
}
