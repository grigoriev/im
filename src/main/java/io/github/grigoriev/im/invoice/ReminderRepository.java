package io.github.grigoriev.im.invoice;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the payment reminders. Entries are only added. */
interface ReminderRepository extends ListCrudRepository<Reminder, Long> {

    /**
     * Returns the reminders of an invoice.
     *
     * @param invoiceId the invoice
     * @return its reminders, oldest first
     */
    List<Reminder> findByInvoiceIdOrderByRemindedOnAscIdAsc(long invoiceId);
}
