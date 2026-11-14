package io.github.grigoriev.im.mail;

import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.StoredDocument;
import io.github.grigoriev.im.validation.Fields;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends an issued invoice by e-mail over SMTP, with its stored PDF and XML byte for byte, and records the dispatch.
 * A failed send records nothing.
 */
@Service
public class MailService {

    static final String NOT_CONFIGURED = "Das Versenden per E-Mail ist nicht eingerichtet.";
    static final String FAILED = "Die E-Mail ging nicht raus. Bitte später noch einmal versuchen oder den Versand "
            + "von Hand vermerken.";
    static final String NOT_RECORDED = "Die E-Mail ist raus, aber der Versand ließ sich nicht vermerken. Bitte den "
            + "Versand von Hand vermerken.";
    static final int MAX_SUBJECT = 200;
    static final int MAX_BODY = 4000;

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private final MailProperties properties;
    private final JavaMailSender sender;
    private final DispatchService dispatches;
    private final DocumentStore documents;
    private final MailTextService texts;

    /**
     * Creates the service.
     *
     * @param properties the SMTP server
     * @param sender sends the message
     * @param dispatches checks the invoice and records the dispatch
     * @param documents the stored PDF and XML
     * @param texts the text of the e-mail
     */
    public MailService(MailProperties properties, JavaMailSender sender, DispatchService dispatches,
            DocumentStore documents, MailTextService texts) {
        this.properties = properties;
        this.sender = sender;
        this.dispatches = dispatches;
        this.documents = documents;
        this.texts = texts;
    }

    /**
     * Tells whether the app can send.
     *
     * @return {@code true} if a server and a sender are set
     */
    public boolean configured() {
        return properties.configured();
    }

    /**
     * Returns the form for an issued invoice: the buyer's address, the text of the e-mail, and the attachments of its
     * format. ZUGFeRD sends the PDF, which carries the XML; XRechnung sends the XML and the PDF as a readable copy.
     *
     * @param invoice the issued invoice
     * @param buyerEmail the buyer's address from the snapshot, may be {@code null}
     * @return the filled form
     */
    public SendForm form(Invoice invoice, String buyerEmail) {
        MailText text = texts.forInvoice(invoice);
        SendForm form = new SendForm();
        form.setTo(buyerEmail);
        form.setSubject(text.subject());
        form.setBody(text.body());
        form.setPdf(true);
        form.setXml(!invoice.format().isPdf());
        return form;
    }

    /**
     * Sends an issued invoice and records the dispatch.
     *
     * @param invoiceId the invoice
     * @param form the entered values
     * @throws InvoiceException if sending is not set up, the invoice cannot go out, a value is wrong or the send fails
     */
    public void send(long invoiceId, SendForm form) {
        if (!configured()) {
            throw new InvoiceException(List.of(NOT_CONFIGURED));
        }
        Invoice invoice = dispatches.sendable(invoiceId);
        String to = Fields.text(form.getTo());
        String subject = Fields.text(form.getSubject());
        String body = form.getBody() == null || form.getBody().isBlank() ? null : form.getBody().strip();
        List<String> problems = new ArrayList<>();
        Fields.checkEmail(problems, to);
        required(problems, subject, "den Betreff", "Der Betreff", MAX_SUBJECT);
        required(problems, body, "den Text", "Der Text", MAX_BODY);
        List<StoredDocument> attachments = new ArrayList<>();
        if (form.isPdf()) {
            documents.find(invoiceId, DocumentKind.PDF).ifPresent(attachments::add);
        }
        if (form.isXml()) {
            documents.find(invoiceId, DocumentKind.XML).ifPresent(attachments::add);
        }
        if (attachments.isEmpty()) {
            problems.add("Bitte PDF, XML oder beides anhängen.");
        }
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        try {
            sender.send(message(to, subject, body, attachments));
        } catch (MailException | MessagingException e) {
            LOG.warn("Sending invoice {} to {} failed: {}", invoice.number(), to, e.getMessage());
            throw new InvoiceException(List.of(FAILED));
        }
        try {
            dispatches.recordEmail(invoiceId, to,
                    String.join(", ", attachments.stream().map(file -> file.kind().name()).toList()));
        } catch (RuntimeException e) {
            LOG.warn("Invoice {} went to {}, but its dispatch was not recorded", invoice.number(), to, e);
            throw new InvoiceException(List.of(NOT_RECORDED));
        }
    }

    /** Requires a value of at most {@code max} characters, with German grammar in both messages. */
    private static void required(List<String> problems, String value, String accusative, String nominative, int max) {
        if (value == null) {
            problems.add("Bitte " + accusative + " angeben.");
        } else {
            Fields.optional(problems, value, nominative, max);
        }
    }

    private MimeMessage message(String to, String subject, String body, List<StoredDocument> attachments)
            throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(properties.from());
        helper.setTo(to);
        if (!properties.bcc().isBlank()) {
            helper.setBcc(properties.bcc());
        }
        helper.setSubject(subject);
        helper.setText(body);
        for (StoredDocument file : attachments) {
            helper.addAttachment(file.filename(), new ByteArrayResource(file.content()), file.kind().mediaType());
        }
        return message;
    }
}
