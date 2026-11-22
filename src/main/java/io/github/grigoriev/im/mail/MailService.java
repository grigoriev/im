package io.github.grigoriev.im.mail;

import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.ReminderService;
import io.github.grigoriev.im.invoice.InvoiceOriginal;
import io.github.grigoriev.im.invoice.OriginalStore;
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
 * Sends an issued invoice by e-mail over SMTP, with its stored PDF and XML byte for byte, and records the dispatch. An
 * external invoice goes out with its original files.
 * The e-mail has the text as typed and the same text as HTML with the facts of the invoice. A failed send records
 * nothing.
 */
@Service
public class MailService {

    static final String NOT_CONFIGURED = "Das Versenden per E-Mail ist nicht eingerichtet.";
    static final String FAILED = "Die E-Mail ging nicht raus. Bitte später noch einmal versuchen oder den Versand "
            + "von Hand vermerken.";
    static final String NOT_RECORDED = "Die E-Mail ist raus, aber der Versand ließ sich nicht vermerken. Bitte den "
            + "Versand von Hand vermerken.";
    static final String REMINDER_NOT_RECORDED = "Die Erinnerung ist raus, aber sie ließ sich nicht vermerken. Bitte "
            + "die Erinnerung von Hand vermerken.";
    static final int MAX_SUBJECT = 200;
    static final int MAX_BODY = 4000;
    /** The length of the note of a dispatch. */
    static final int MAX_NOTE = 500;

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private final MailProperties properties;
    private final JavaMailSender sender;
    private final DispatchService dispatches;
    private final DocumentStore documents;
    private final OriginalStore originals;
    private final MailTextService texts;
    private final MailHtml html;
    private final ReminderService reminders;

    /**
     * Creates the service.
     *
     * @param properties the SMTP server
     * @param sender sends the message
     * @param dispatches checks the invoice and records the dispatch
     * @param documents the stored PDF and XML
     * @param originals the original files of an external invoice
     * @param texts the text of the e-mail
     * @param html writes the HTML of the e-mail
     * @param reminders checks and records a payment reminder
     */
    @SuppressWarnings("java:S107") // the parts of an e-mail
    public MailService(MailProperties properties, JavaMailSender sender, DispatchService dispatches,
            DocumentStore documents, OriginalStore originals, MailTextService texts, MailHtml html,
            ReminderService reminders) {
        this.properties = properties;
        this.sender = sender;
        this.dispatches = dispatches;
        this.documents = documents;
        this.originals = originals;
        this.texts = texts;
        this.html = html;
        this.reminders = reminders;
    }

    /**
     * Shows the e-mail as the customer sees it: the HTML of the text as typed. It checks nothing and sends nothing.
     *
     * @param invoiceId the invoice
     * @param form the entered values
     * @return the HTML
     * @throws InvoiceException if the invoice cannot go out
     */
    public String preview(long invoiceId, SendForm form) {
        Invoice invoice = dispatches.sendable(invoiceId);
        return html.render(documents.verifiedSnapshot(invoice), Fields.text(form.getSubject()), form.getBody());
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
     * format. ZUGFeRD sends the PDF, which carries the XML; XRechnung sends the XML and the PDF as a readable copy; an
     * external invoice sends its original files, which "pdf" stands for.
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
        form.setXml(!invoice.format().isPdf() && !invoice.format().isExternal());
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
        Outgoing mail = outgoing(invoiceId, form);
        InvoiceDocument document = documents.verifiedSnapshot(invoice);
        deliver(invoice, mail, html.render(document, mail.subject(), mail.body()), MailHtml.plain(document, mail.body()));
        try {
            dispatches.recordEmail(invoiceId, mail.to(), mail.attachmentNames());
        } catch (RuntimeException e) {
            LOG.warn("Invoice {} went to {}, but its dispatch was not recorded", invoice.number(), mail.to(), e);
            throw new InvoiceException(List.of(NOT_RECORDED));
        }
    }

    /**
     * Returns the form of a payment reminder: the buyer's address, the reminder text with the open amount, and the PDF.
     *
     * @param invoice the issued invoice that waits for its money
     * @param buyerEmail the buyer's address from the snapshot, may be {@code null}
     * @return the filled form
     */
    public SendForm reminderForm(Invoice invoice, String buyerEmail) {
        MailText text = texts.forReminder(invoice, reminders.open(invoice));
        SendForm form = new SendForm();
        form.setTo(buyerEmail);
        form.setSubject(text.subject());
        form.setBody(text.body());
        form.setPdf(true);
        return form;
    }

    /**
     * Shows a payment reminder as the customer sees it. It checks nothing and sends nothing.
     *
     * @param invoiceId the invoice
     * @param form the entered values
     * @return the HTML
     * @throws InvoiceException if the invoice waits for nothing
     */
    public String previewReminder(long invoiceId, SendForm form) {
        Invoice invoice = reminders.remindable(invoiceId);
        return html.renderReminder(documents.verifiedSnapshot(invoice), Fields.text(form.getSubject()), form.getBody(),
                reminders.open(invoice));
    }

    /**
     * Sends a payment reminder with the invoice attached and records it.
     *
     * @param invoiceId the invoice
     * @param form the entered values
     * @throws InvoiceException if sending is not set up, the invoice waits for nothing, a value is wrong or the send
     *     fails
     */
    public void sendReminder(long invoiceId, SendForm form) {
        if (!configured()) {
            throw new InvoiceException(List.of(NOT_CONFIGURED));
        }
        Invoice invoice = reminders.remindable(invoiceId);
        Outgoing mail = outgoing(invoiceId, form);
        InvoiceDocument document = documents.verifiedSnapshot(invoice);
        deliver(invoice, mail, html.renderReminder(document, mail.subject(), mail.body(), reminders.open(invoice)),
                MailHtml.plain(document, mail.body()));
        try {
            reminders.recordEmail(invoiceId, mail.to());
        } catch (RuntimeException e) {
            LOG.warn("The reminder of invoice {} went to {}, but was not recorded", invoice.number(), mail.to(), e);
            throw new InvoiceException(List.of(REMINDER_NOT_RECORDED));
        }
    }

    /** A file to attach: a stored document of the app or an original file. */
    private static final class Attachment {

        private final String filename;
        private final String mediaType;
        private final byte[] content;
        private final String label;

        Attachment(String filename, String mediaType, byte[] content, String label) {
            this.filename = filename;
            this.mediaType = mediaType;
            this.content = content;
            this.label = label;
        }

        static Attachment of(StoredDocument document) {
            return new Attachment(document.filename(), document.kind().mediaType(), document.content(),
                    document.kind().name());
        }

        static Attachment of(InvoiceOriginal original) {
            return new Attachment(original.filename(), original.mediaType(), original.content(), original.filename());
        }
    }

    /** A checked e-mail: address, subject, text and the stored files to attach. */
    private record Outgoing(String to, String subject, String body, List<Attachment> attachments) {

        String attachmentNames() {
            return note(attachments.stream().map(file -> file.label).toList());
        }
    }

    /**
     * Names the attachments in the note of a dispatch, which holds 500 characters: the names that fit, then how many
     * more there are.
     *
     * @param names the names of the attachments
     * @return for example {@code PDF, XML}, or {@code a.pdf, b.pdf und 3 weitere}
     */
    static String note(List<String> names) {
        String all = String.join(", ", names);
        if (all.length() <= MAX_NOTE) {
            return all;
        }
        // The full list is too long, so the loop stops before it takes the last name.
        int listed = 0;
        while (String.join(", ", names.subList(0, listed + 1)).length() + more(names.size() - listed - 1).length()
                <= MAX_NOTE) {
            listed++;
        }
        return listed == 0 ? names.size() + " Dateien"
                : String.join(", ", names.subList(0, listed)) + more(names.size() - listed);
    }

    private static String more(int count) {
        return " und " + count + " weitere";
    }

    /** Checks the entered values and gathers the attachments. */
    private Outgoing outgoing(long invoiceId, SendForm form) {
        String to = Fields.text(form.getTo());
        String subject = Fields.text(form.getSubject());
        String body = form.getBody() == null || form.getBody().isBlank() ? null : form.getBody().strip();
        List<String> problems = new ArrayList<>();
        Fields.checkEmail(problems, to);
        required(problems, subject, "den Betreff", "Der Betreff", MAX_SUBJECT);
        required(problems, body, "den Text", "Der Text", MAX_BODY);
        List<Attachment> attachments = new ArrayList<>();
        if (form.isPdf()) {
            documents.find(invoiceId, DocumentKind.PDF).map(Attachment::of).ifPresent(attachments::add);
            // An invoice of the app has no original files: only an external one adds them here.
            originals.list(invoiceId).stream().map(originals::verified).map(Attachment::of).forEach(attachments::add);
        }
        if (form.isXml()) {
            documents.find(invoiceId, DocumentKind.XML).map(Attachment::of).ifPresent(attachments::add);
        }
        if (attachments.isEmpty()) {
            problems.add("Bitte die Rechnung anhängen: PDF, XML oder die Originaldateien.");
        }
        if (!problems.isEmpty()) {
            throw new InvoiceException(problems);
        }
        return new Outgoing(to, subject, body, attachments);
    }

    private void deliver(Invoice invoice, Outgoing mail, String htmlBody, String plainBody) {
        try {
            sender.send(message(mail.to(), mail.subject(), plainBody, htmlBody, mail.attachments()));
        } catch (MailException | MessagingException e) {
            LOG.warn("Sending invoice {} to {} failed: {}", invoice.number(), mail.to(), e.getMessage());
            throw new InvoiceException(List.of(FAILED));
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

    private MimeMessage message(String to, String subject, String body, String htmlBody,
            List<Attachment> attachments) throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(properties.from());
        helper.setTo(to);
        if (!properties.bcc().isBlank()) {
            helper.setBcc(properties.bcc());
        }
        helper.setSubject(subject);
        helper.setText(body, htmlBody);
        for (Attachment file : attachments) {
            helper.addAttachment(file.filename, new ByteArrayResource(file.content), file.mediaType);
        }
        return message;
    }
}
