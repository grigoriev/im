package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.TestDatabase;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

/** The version of an invoice in PostgreSQL: a save with an older version fails as the service expects. */
@SpringBootTest
@Import(TestDatabase.class)
class InvoiceRepositoryIT {

    @Autowired
    private InvoiceRepository invoices;

    @Autowired
    private CustomerService customers;

    @Test
    void aSaveWithAnOlderVersionFails() {
        Customer customer = customers.create(CustomerTestData.verlag());
        LocalDate day = LocalDate.of(2027, 3, 18);
        Invoice first = invoices.save(new Invoice(null, InvoiceStatus.DRAFT, customer.id(), null,
                InvoiceFormat.ZUGFERD_EN16931, day, day, day, 14, null, null, null, null, List.of(), Instant.EPOCH,
                Instant.EPOCH, null));
        assertThat(first.version()).isZero();
        Invoice second = invoices.save(new Invoice(first.id(), first.status(), first.customerId(), null, first.format(),
                day, day, day, 30, null, null, null, null, List.of(), Instant.EPOCH, Instant.EPOCH, first.version()));
        assertThat(second.version()).isEqualTo(1L);

        Invoice stale = new Invoice(first.id(), first.status(), first.customerId(), null, first.format(), day, day, day, 7,
                null, null, null, null, List.of(), Instant.EPOCH, Instant.EPOCH, first.version());
        assertThatThrownBy(() -> invoices.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(invoices.findById(first.id()).orElseThrow().paymentDays()).isEqualTo(30);
        invoices.deleteById(first.id());
        customers.delete(customer.id());
    }
}
