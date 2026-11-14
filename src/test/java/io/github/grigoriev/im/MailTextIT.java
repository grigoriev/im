package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.TemplateForm;
import io.github.grigoriev.im.invoice.TemplateService;
import io.github.grigoriev.im.mail.MailText;
import io.github.grigoriev.im.mail.MailTextService;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The e-mail text against PostgreSQL: the default in the settings and the template an issued invoice came from. */
@SpringBootTest
@Import(TestDatabase.class)
class MailTextIT {

    @Autowired
    private MailTextService texts;

    @Autowired
    private TemplateService templates;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private IssueService issues;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private NumberService numbers;

    @Test
    void anIssuedInvoiceSendsTheTextOfItsTemplateUntilTheTemplateGoes() {
        sellers.save(SellerTestData.profile());
        numbers.savePattern("{YYYY}-{NN}");
        Customer customer = customers.create(CustomerTestData.verlag());
        InvoiceTemplate template = templates.create(customer.id());
        TemplateForm form = TemplateForm.of(template);
        form.setName("Lesung");
        form.setEmailSubject("Lesung {Leistungszeitraum}, Rechnung {Rechnungsnummer}");
        form.setEmailBody("");
        LineForm line = form.getLines().getFirst();
        line.setText("Lesung");
        line.setAmount("400");
        line.setCategory(TaxCategory.KLEINUNTERNEHMER);
        templates.save(customer.id(), template.id(), form);

        Invoice draft = invoices.createDraft(customer.id(), template.id());
        DraftForm dates = DraftForm.of(draft);
        LocalDate day = LocalDate.of(2040, 6, 3);
        dates.setIssueDate(day);
        dates.setServiceFrom(day);
        dates.setServiceTo(day);
        dates.setBuyerReference(customer.buyerReference());
        Invoice saved = invoices.saveDraft(draft.id(), dates);
        issues.issue(saved.id(), saved.version());
        Invoice issued = invoices.find(draft.id()).orElseThrow();

        MailText text = texts.forInvoice(issued);
        assertThat(text.subject()).isEqualTo("Lesung 03.06.2040, Rechnung 2040-01");
        assertThat(text.body()).as("the template has no text, so the default fills in")
                .contains("meine Rechnung 2040-01 vom 03.06.2040");

        templates.delete(customer.id(), template.id());
        assertThat(invoices.template(issued)).as("deleting the template unlinks the issued invoice").isEmpty();
        assertThat(texts.forInvoice(issued).subject()).isEqualTo("Rechnung 2040-01");
    }

    @Test
    void keepsTheLongestDefaultText() {
        texts.save("s".repeat(200), "b".repeat(4000));
        assertThat(texts.defaults()).isEqualTo(new MailText("s".repeat(200), "b".repeat(4000)));
        texts.save("", "");
        assertThat(texts.defaults().subject()).isEqualTo("Rechnung {Rechnungsnummer}");
    }
}
