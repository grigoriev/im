package io.github.grigoriev.im.mail;

import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import io.github.grigoriev.im.einvoice.InvoiceReference;
import io.github.grigoriev.im.einvoice.Party;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Writes the HTML part of the e-mail with an invoice: the text as typed, in paragraphs, then a box with the facts of
 * the invoice and the bank account, then the address of the seller. The text part stays as typed.
 */
@Component
public class MailHtml {

    private static final Locale GERMAN = Locale.GERMANY;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", GERMAN);

    private final ITemplateEngine templates;

    /**
     * Creates the writer.
     *
     * @param templates the Thymeleaf engine that holds {@code mail/invoice.html}
     */
    public MailHtml(ITemplateEngine templates) {
        this.templates = templates;
    }

    /**
     * One line of the box with the facts.
     *
     * @param label the German label, for example "Zahlbar bis"
     * @param value the value
     */
    public record Fact(String label, String value) {
    }

    /**
     * Writes the HTML of an e-mail.
     *
     * @param document the issued invoice
     * @param subject the subject, the title of the HTML
     * @param body the text as typed, may be {@code null}
     * @return the HTML
     */
    public String render(InvoiceDocument document, String subject, String body) {
        Context context = new Context(GERMAN, Map.of("subject", subject == null ? "" : subject,
                "paragraphs", paragraphs(body), "heading", heading(document), "facts", facts(document),
                "signature", signature(document.seller())));
        return templates.process("mail/invoice", context);
    }

    /**
     * Splits the text into paragraphs at blank lines, and each paragraph into its lines.
     *
     * @param body the text, may be {@code null}
     * @return the paragraphs, none for a blank text
     */
    static List<List<String>> paragraphs(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return Arrays.stream(body.strip().replace("\r\n", "\n").split("\n\\s*\n"))
                .map(paragraph -> paragraph.lines().map(String::strip).toList()).toList();
    }

    static String heading(InvoiceDocument document) {
        return (document.cancellation() ? "Stornorechnung " : "Rechnung ") + document.number();
    }

    /** The facts of the box: date, amount, due date and bank account; a Stornorechnung names its invoice instead. */
    static List<Fact> facts(InvoiceDocument document) {
        InvoicePdfRenderer.Formatter format = new InvoicePdfRenderer.Formatter();
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("Rechnungsdatum", DATE.format(document.issueDate())));
        InvoiceReference cancels = document.cancels();
        if (cancels != null) {
            facts.add(new Fact("Storno zu", "Rechnung " + cancels.number() + " vom " + DATE.format(cancels.issueDate())));
        }
        facts.add(new Fact("Betrag", format.money(document.total())));
        if (cancels == null) {
            facts.add(new Fact("Zahlbar bis", DATE.format(document.dueDate())));
            facts.add(new Fact("IBAN", iban(document.iban())));
            if (!document.bic().isBlank()) {
                facts.add(new Fact("BIC", document.bic()));
            }
            facts.add(new Fact("Verwendungszweck", document.number()));
        }
        return facts;
    }

    /**
     * Returns the text part of the e-mail: the text as typed and below it the same signature as the HTML part.
     *
     * @param document the invoice
     * @param body the text of the e-mail
     * @return the text with the signature
     */
    public static String plain(InvoiceDocument document, String body) {
        return body + "\n\n" + String.join("\n", signature(document.seller()));
    }

    /** The lines below the text: name, address, e-mail and phone, the empty ones left out. */
    static List<String> signature(Party seller) {
        List<String> lines = new ArrayList<>();
        lines.add(seller.name());
        lines.add(seller.street());
        lines.add(seller.postcode() + " " + seller.city());
        String contact = String.join(" · ", Arrays.stream(new String[] {seller.email(), seller.phone()})
                .filter(value -> value != null && !value.isBlank()).toList());
        if (!contact.isEmpty()) {
            lines.add(contact);
        }
        return lines;
    }

    /**
     * Writes an IBAN in groups of four, as on paper.
     *
     * @param iban the IBAN
     * @return the grouped IBAN
     */
    static String iban(String iban) {
        return iban.replaceAll("\\s+", "").replaceAll("(.{4})(?=.)", "$1 ");
    }
}
