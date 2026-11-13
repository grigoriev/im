package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Customers against PostgreSQL. */
@SpringBootTest
@Import(TestDatabase.class)
class CustomerIT {

    @Autowired
    private CustomerService customers;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsChangesListsAndDeletesCustomers() {
        Customer vhs = customers.create(CustomerTestData.vhs());
        Customer verlag = customers.create(CustomerTestData.verlag());
        assertThat(vhs.id()).isNotNull();
        assertThat(customers.find(vhs.id())).contains(vhs);
        assertThat(customers.list()).extracting(Customer::name).containsSubsequence("Verlag am Fluss GmbH",
                "Volkshochschule Musterstadt");

        Customer v = CustomerTestData.verlag();
        customers.update(verlag.id(), new Customer(null, v.name(), "Kai 7", v.postcode(), v.city(), v.countryCode(),
                null, v.email(), "DE123456789", null, InvoiceFormat.ZUGFERD_EN16931));
        Customer changed = customers.find(verlag.id()).orElseThrow();
        assertThat(changed.street()).isEqualTo("Kai 7");
        assertThat(changed.vatId()).isEqualTo("DE123456789");
        assertThat(changed.format()).isEqualTo(InvoiceFormat.ZUGFERD_EN16931);

        customers.delete(verlag.id());
        assertThat(customers.find(verlag.id())).isEmpty();
        customers.delete(vhs.id());
    }

    @Test
    void theDatabaseKnowsOnlyTheThreeFormats() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customer (name, street, postcode, city, country_code, format) "
                + "VALUES ('n', 's', 'p', 'c', 'DE', 'PDF')")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
