package io.github.grigoriev.im.mail;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.invoice.Placeholders;
import io.github.grigoriev.im.seller.SettingsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The text of the e-mail with an invoice. The admin sets the default; a template overrides it per field. The
 * placeholders of the invoice texts work here too.
 */
@Service
public class MailTextService {

    static final String SUBJECT_KEY = "mail-subject";
    static final String BODY_KEY = "mail-body";
    static final String DEFAULT_SUBJECT = "Rechnung {Rechnungsnummer}";
    static final String DEFAULT_BODY = """
            Sehr geehrte Damen und Herren,

            anbei erhalten Sie meine Rechnung {Rechnungsnummer} vom {Rechnungsdatum}, zahlbar bis {Zahlungsziel}.

            Mit freundlichen Grüßen""";
    static final int MAX_SUBJECT = 200;
    static final int MAX_BODY = 4000;

    static final String READ = "SELECT value FROM app_setting WHERE key = :key";
    static final String WRITE = """
            INSERT INTO app_setting (key, value) VALUES (:key, :value)
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value""";
    static final String RESET = "DELETE FROM app_setting WHERE key = :key";

    private final JdbcClient jdbc;
    private final InvoiceService invoices;
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param jdbc reads and writes the default text
     * @param invoices the template of an invoice
     * @param audit the audit log
     */
    public MailTextService(JdbcClient jdbc, InvoiceService invoices, AuditService audit) {
        this.jdbc = jdbc;
        this.invoices = invoices;
        this.audit = audit;
    }

    /**
     * Returns the default text, with its placeholders.
     *
     * @return the text the admin set, or the built-in one
     */
    public MailText defaults() {
        return new MailText(read(SUBJECT_KEY).orElse(DEFAULT_SUBJECT), read(BODY_KEY).orElse(DEFAULT_BODY));
    }

    /**
     * Saves the default text. A blank field or the built-in text goes back to the built-in one.
     *
     * @param subject the subject
     * @param body the text
     * @throws SettingsException if a field is too long
     */
    @Transactional
    public void save(String subject, String body) {
        String newSubject = subject == null ? "" : subject.strip();
        String newBody = body == null ? "" : body.strip().replace("\r\n", "\n");
        List<String> problems = new ArrayList<>();
        if (newSubject.length() > MAX_SUBJECT) {
            problems.add("Der Betreff hat höchstens " + MAX_SUBJECT + " Zeichen.");
        }
        if (newBody.length() > MAX_BODY) {
            problems.add("Der Text hat höchstens " + MAX_BODY + " Zeichen.");
        }
        if (!problems.isEmpty()) {
            throw new SettingsException(problems);
        }
        write(SUBJECT_KEY, newSubject, DEFAULT_SUBJECT);
        write(BODY_KEY, newBody, DEFAULT_BODY);
        audit.add(AuditAction.MAIL_TEXT_SAVED, "");
    }

    /**
     * Returns the text of the e-mail with an issued invoice: the template's subject and text where it has them, else
     * the default, with the placeholders filled in.
     *
     * @param invoice the issued invoice
     * @return the text to send
     */
    public MailText forInvoice(Invoice invoice) {
        MailText defaults = defaults();
        Optional<InvoiceTemplate> template = invoices.template(invoice);
        String subject = template.map(InvoiceTemplate::emailSubject).filter(text -> !text.isBlank())
                .orElse(defaults.subject());
        String body = template.map(InvoiceTemplate::emailBody).filter(text -> !text.isBlank()).orElse(defaults.body());
        Placeholders values = Placeholders.of(invoice, invoice.number());
        return new MailText(values.fill(subject), values.fill(body));
    }

    private Optional<String> read(String key) {
        return jdbc.sql(READ).param("key", key).query(String.class).optional();
    }

    private void write(String key, String value, String builtIn) {
        if (value.isEmpty() || value.equals(builtIn)) {
            jdbc.sql(RESET).param("key", key).update();
        } else {
            jdbc.sql(WRITE).param("key", key).param("value", value).update();
        }
    }
}
