package io.github.grigoriev.im.audit;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the audit log. Entries are only added, never changed. */
interface AuditRepository extends ListCrudRepository<AuditEvent, Long> {

    /**
     * Returns the newest entries.
     *
     * @return at most 200 entries, newest first
     */
    List<AuditEvent> findTop200ByOrderByAtDescIdDesc();

    /**
     * Returns the history of an invoice.
     *
     * @param invoiceId the invoice
     * @return its entries, oldest first
     */
    List<AuditEvent> findByInvoiceIdOrderByAtAscIdAsc(long invoiceId);
}
