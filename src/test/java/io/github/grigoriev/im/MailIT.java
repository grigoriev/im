package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DispatchChannel;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.mail.MailService;
import io.github.grigoriev.im.mail.SendForm;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Sending an issued invoice over SMTP to GreenMail: the message, the stored attachments and the dispatch. */
@SpringBootTest(properties = {"im.mail.host=localhost", "im.mail.port=3025", "im.mail.starttls=false",
        "im.mail.from=rechnung@elena.example", "im.mail.bcc=elena@elena.example"})
@Import(TestDatabase.class)
class MailIT {

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private MailService mail;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private IssueService issues;

    @Autowired
    private DispatchService dispatches;

    @Autowired
    private DocumentStore documents;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private NumberService numbers;

    private Invoice issued() {
        sellers.save(SellerTestData.profile());
        numbers.savePattern("{YYYY}-{NN}");
        Customer customer = customers.create(CustomerTestData.verlag());
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        LocalDate day = LocalDate.of(2039, 2, 1);
        form.setIssueDate(day);
        form.setServiceFrom(day);
        form.setServiceTo(day);
        form.setBuyerReference(customer.buyerReference());
        LineForm line = form.getLines().getFirst();
        line.setText("Lesung");
        line.setAmount("400");
        line.setCategory(TaxCategory.KLEINUNTERNEHMER);
        Invoice saved = invoices.saveDraft(draft.id(), form);
        issues.issue(saved.id(), saved.version());
        return invoices.find(draft.id()).orElseThrow();
    }

    @Test
    void sendsTheStoredDocumentsAndRecordsTheDispatch() throws Exception {
        Invoice invoice = issued();
        assertThat(mail.configured()).isTrue();
        SendForm form = mail.form(invoice, "buchhaltung@verlag.example");
        assertThat(form.getSubject()).isEqualTo("Rechnung 2039-01");
        form.setXml(true);
        mail.send(invoice.id(), form);

        MimeMessage[] received = SMTP.getReceivedMessages();
        assertThat(received).as("the buyer and the blind copy").hasSize(2);
        MimeMessage message = received[0];
        assertThat(message.getFrom()[0]).hasToString("rechnung@elena.example");
        assertThat(message.getSubject()).isEqualTo("Rechnung 2039-01");
        Multipart parts = (Multipart) message.getContent();
        assertThat(parts.getCount()).isEqualTo(3);
        Part pdf = parts.getBodyPart(1);
        assertThat(pdf.getFileName()).isEqualTo("Rechnung_2039-01.pdf");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        pdf.getDataHandler().writeTo(bytes);
        assertThat(bytes.toByteArray()).as("the stored PDF, byte for byte")
                .isEqualTo(documents.find(invoice.id(), DocumentKind.PDF).orElseThrow().content());
        assertThat(parts.getBodyPart(2).getFileName()).isEqualTo("Rechnung_2039-01.xml");

        assertThat(invoices.find(invoice.id()).orElseThrow().status()).isEqualTo(InvoiceStatus.SENT);
        assertThat(dispatches.list(invoice.id())).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.channel()).isEqualTo(DispatchChannel.EMAIL_APP);
            assertThat(dispatch.recipient()).isEqualTo("buchhaltung@verlag.example");
            assertThat(dispatch.note()).isEqualTo("PDF, XML");
        });
    }
}
