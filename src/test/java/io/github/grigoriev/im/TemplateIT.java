package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DraftLine;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.TemplateForm;
import io.github.grigoriev.im.invoice.TemplateLine;
import io.github.grigoriev.im.invoice.TemplateService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Templates against PostgreSQL: their lines, the drafts made from them, and the customer they go with. */
@SpringBootTest
@Import(TestDatabase.class)
class TemplateIT {

    @Autowired
    private TemplateService templates;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private JdbcTemplate jdbc;

    private static LineForm row(String text, String amount) {
        LineForm row = new LineForm();
        row.setText(text);
        row.setAmount(amount);
        row.setCategory(TaxCategory.KLEINUNTERNEHMER);
        return row;
    }

    @Test
    void aDraftCopiesItsTemplateAndKeepsItsCopy() {
        SellerTestData.save(sellers, SellerTestData.profile());
        Customer customer = customers.create(CustomerTestData.verlag());
        InvoiceTemplate template = templates.create(customer.id());
        TemplateForm form = TemplateForm.of(template);
        form.setName("Lesung mit Gespräch");
        form.getLines().clear();
        form.getLines().add(row("Lesung", "400"));
        form.getLines().add(row("Reisekosten", "80"));
        InvoiceTemplate saved = templates.save(customer.id(), template.id(), form);
        assertThat(templates.list(customer.id())).extracting(InvoiceTemplate::name).containsExactly("Lesung mit Gespräch");
        assertThat(saved.lines()).extracting(TemplateLine::text).containsExactly("Lesung", "Reisekosten");

        Invoice draft = invoices.createDraft(customer.id(), template.id());
        assertThat(draft.lines()).extracting(DraftLine::text).containsExactly("Lesung", "Reisekosten");
        assertThat(invoices.document(draft).orElseThrow().total()).isEqualByComparingTo("480.00");

        TemplateForm changed = TemplateForm.of(saved);
        changed.getLines().removeFirst();
        templates.save(customer.id(), template.id(), changed);
        assertThat(invoices.find(draft.id()).orElseThrow().lines()).as("the draft keeps its copy").hasSize(2);

        invoices.deleteDraft(draft.id());
        customers.delete(customer.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_template WHERE customer_id = ?", Integer.class,
                customer.id())).as("the templates go with their customer").isZero();
    }
}
