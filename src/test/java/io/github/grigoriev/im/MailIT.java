package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DispatchChannel;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.ReminderChannel;
import io.github.grigoriev.im.invoice.ReminderService;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.ExternalInvoiceService;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.SampleOffice;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private ReminderService reminders;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentStore documents;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private NumberService numbers;

    @Autowired
    private ExternalInvoiceService externals;

    private Invoice issued() {
        SellerTestData.save(sellers, SellerTestData.profile());
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
        assertThat(form.getSubject()).isEqualTo("Rechnung " + invoice.number());
        form.setXml(true);
        mail.send(invoice.id(), form);

        MimeMessage[] received = SMTP.getReceivedMessages();
        assertThat(received).as("the buyer and the blind copy").hasSize(2);
        MimeMessage message = received[0];
        assertThat(message.getFrom()[0]).hasToString("rechnung@elena.example");
        assertThat(message.getSubject()).isEqualTo("Rechnung " + invoice.number());
        Multipart parts = (Multipart) message.getContent();
        assertThat(parts.getCount()).isEqualTo(3);
        Part pdf = parts.getBodyPart(1);
        assertThat(pdf.getFileName()).isEqualTo("Rechnung_" + invoice.number() + ".pdf");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        pdf.getDataHandler().writeTo(bytes);
        assertThat(bytes.toByteArray()).as("the stored PDF, byte for byte")
                .isEqualTo(documents.find(invoice.id(), DocumentKind.PDF).orElseThrow().content());
        assertThat(parts.getBodyPart(2).getFileName()).isEqualTo("Rechnung_" + invoice.number() + ".xml");
        Multipart related = (Multipart) parts.getBodyPart(0).getContent();
        Multipart alternative = (Multipart) related.getBodyPart(0).getContent();
        assertThat(alternative.getBodyPart(0).isMimeType("text/plain")).as("text first, for old programs").isTrue();
        assertThat(alternative.getBodyPart(1).isMimeType("text/html")).isTrue();
        assertThat((String) alternative.getBodyPart(1).getContent()).contains(">Rechnung " + invoice.number() + "</td>");

        assertThat(invoices.find(invoice.id()).orElseThrow().status()).isEqualTo(InvoiceStatus.SENT);
        assertThat(dispatches.list(invoice.id())).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.channel()).isEqualTo(DispatchChannel.EMAIL_APP);
            assertThat(dispatch.recipient()).isEqualTo("buchhaltung@verlag.example");
            assertThat(dispatch.note()).isEqualTo("PDF, XML");
        });
    }

    @Test
    void sendsTheOriginalFilesOfAnExternalInvoice() throws Exception {
        SellerTestData.save(sellers, SellerTestData.profile());
        Customer customer = customers.create(CustomerTestData.verlag());
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        LocalDate day = LocalDate.of(2001, 2, 1);
        form.setFormat(InvoiceFormat.EXTERNAL);
        form.setIssueDate(day);
        form.setServiceFrom(day);
        form.setServiceTo(day);
        form.getLines().getFirst().setText("Lesung");
        form.getLines().getFirst().setAmount("400");
        Invoice saved = invoices.saveDraft(draft.id(), form);
        byte[] xlsx = SampleOffice.workbook();
        String number = externals.register(saved.id(), saved.version(), externals.nextNumber(saved).orElseThrow(),
                "Rechnung Februar.xlsx", xlsx);
        Invoice external = invoices.find(saved.id()).orElseThrow();

        SendForm send = mail.form(external, "buchhaltung@verlag.example");
        mail.send(external.id(), send);
        MimeMessage message = SMTP.getReceivedMessages()[0];
        Multipart parts = (Multipart) message.getContent();
        assertThat(parts.getCount()).isEqualTo(2);
        Part file = parts.getBodyPart(1);
        assertThat(file.getFileName()).isEqualTo("Rechnung Februar.xlsx");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        file.getDataHandler().writeTo(bytes);
        assertThat(bytes.toByteArray()).as("the original file, byte for byte").isEqualTo(xlsx);
        assertThat(dispatches.list(external.id())).singleElement()
                .satisfies(dispatch -> assertThat(dispatch.note()).isEqualTo("Rechnung Februar.xlsx"));
        assertThat(number).startsWith("2001-");
    }

    @Test
    void sendsAReminderAndKeepsItInTheHistory() throws Exception {
        Invoice invoice = issued();
        SendForm form = mail.reminderForm(invoice, "buchhaltung@verlag.example");
        assertThat(form.getSubject()).isEqualTo("Zahlungserinnerung zur Rechnung " + invoice.number());
        assertThat(form.getBody()).contains("Offen sind noch 400,00\u00a0€.");
        mail.sendReminder(invoice.id(), form);

        MimeMessage message = SMTP.getReceivedMessages()[0];
        assertThat(message.getSubject()).isEqualTo("Zahlungserinnerung zur Rechnung " + invoice.number());
        Multipart parts = (Multipart) message.getContent();
        assertThat(parts.getCount()).as("the text and the PDF").isEqualTo(2);
        assertThat(reminders.list(invoice.id())).singleElement().satisfies(reminder -> {
            assertThat(reminder.channel()).isEqualTo(ReminderChannel.EMAIL_APP);
            assertThat(reminder.recipient()).isEqualTo("buchhaltung@verlag.example");
        });
        assertThat(reminders.last()).containsKey(invoice.id());
        assertThat(invoices.find(invoice.id()).orElseThrow().status()).as("a reminder is no dispatch")
                .isEqualTo(InvoiceStatus.ISSUED);
        assertThatThrownBy(() -> jdbc.update("UPDATE reminder SET note = 'x'")).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM reminder")).hasMessageContaining("immutable");
    }
}
