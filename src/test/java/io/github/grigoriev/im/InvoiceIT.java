package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerException;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.DraftLine;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Draft invoices against PostgreSQL: the lines in order, the customer they hold, the database rules. */
@SpringBootTest
@Import(TestDatabase.class)
class InvoiceIT {

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private JdbcTemplate jdbc;

    private Customer customer;

    @BeforeEach
    void profileAndCustomer() {
        sellers.save(SellerTestData.profile());
        customer = customers.create(CustomerTestData.verlag());
    }

    private static LineForm row(String text, boolean lumpSum, String quantity, String price) {
        LineForm row = new LineForm();
        row.setText(text);
        row.setLumpSum(lumpSum);
        row.setQuantity(quantity);
        row.setPrice(price);
        row.setCategory(TaxCategory.KLEINUNTERNEHMER);
        return row;
    }

    @Test
    void storesADraftWithItsLinesInOrder() {
        Invoice draft = invoices.createDraft(customer.id());
        assertThat(invoices.find(draft.id()).orElseThrow().lines()).containsExactly(DraftLine.empty(TaxCategory.KLEINUNTERNEHMER));

        DraftForm form = DraftForm.of(draft);
        form.getLines().clear();
        form.getLines().add(row("Lesung", true, "1", "400,00"));
        form.getLines().add(row("Reisekosten", true, "1", "80"));
        form.getLines().add(row("Signierstunde", false, "2", "25,50"));
        invoices.saveDraft(draft.id(), form);
        Invoice stored = invoices.find(draft.id()).orElseThrow();
        assertThat(stored.lines()).extracting(DraftLine::text).containsExactly("Lesung", "Reisekosten", "Signierstunde");
        assertThat(invoices.problems(stored)).isEmpty();
        assertThat(invoices.document(stored).orElseThrow().total()).isEqualByComparingTo("531.00");

        form.getLines().remove(1);
        invoices.saveDraft(draft.id(), form);
        assertThat(invoices.find(draft.id()).orElseThrow().lines()).extracting(DraftLine::text)
                .containsExactly("Lesung", "Signierstunde");
        invoices.deleteDraft(draft.id());
        assertThat(invoices.find(draft.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_line WHERE invoice_id = ?", Integer.class, draft.id()))
                .isZero();
    }

    @Test
    void aCustomerWithInvoicesStays() {
        Invoice draft = invoices.createDraft(customer.id());
        assertThatThrownBy(() -> customers.delete(customer.id())).isInstanceOf(CustomerException.class)
                .hasMessage("Der Kunde hat Rechnungen und bleibt erhalten.");
        assertThat(customers.find(customer.id())).isPresent();
        invoices.deleteDraft(draft.id());
        customers.delete(customer.id());
        assertThat(customers.find(customer.id())).isEmpty();
    }

    @Test
    void aDraftHasNoNumberAndAnIssuedInvoiceHasOne() {
        Invoice draft = invoices.createDraft(customer.id());
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET number = '2027-01' WHERE id = ?", draft.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET status = 'ISSUED' WHERE id = ?", draft.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        invoices.deleteDraft(draft.id());
    }
}
