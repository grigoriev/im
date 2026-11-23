package io.github.grigoriev.im.mail;

import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceReference;
import io.github.grigoriev.im.einvoice.Party;
import io.github.grigoriev.im.format.GermanFormat;
import java.math.BigDecimal;
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
        return render(document, subject, body, facts(document));
    }

    /**
     * Writes the HTML of a payment reminder: the box shows the due date and the open amount.
     *
     * @param document the issued invoice
     * @param subject the subject, the title of the HTML
     * @param body the text as typed, may be {@code null}
     * @param open what the invoice still waits for
     * @return the HTML
     */
    public String renderReminder(InvoiceDocument document, String subject, String body, BigDecimal open) {
        return render(document, subject, body, reminderFacts(document, open));
    }

    private String render(InvoiceDocument document, String subject, String body, List<Fact> facts) {
        Letter letter = letter(document.seller(), body);
        Context context = new Context(GERMAN, Map.of("subject", subject == null ? "" : subject,
                "paragraphs", letter.text(), "closing", letter.closing(), "heading", heading(document),
                "facts", facts, "contact", contact(document.seller())));
        return templates.process("mail/invoice", context);
    }

    /**
     * The text of a letter around the box with the facts.
     *
     * @param text the paragraphs above the box
     * @param closing the last paragraph below the box with the name on its last line, empty if the text has one
     *     paragraph only and the name follows it there
     */
    record Letter(List<List<String>> text, List<String> closing) {
    }

    /**
     * Arranges the text as a letter: the paragraphs above the box, the last one, the greeting, below it. The name
     * follows the greeting on the next line, unless the text ends with it already.
     *
     * @param seller the seller, whose contact person or name signs
     * @param body the text as typed, may be {@code null}
     * @return the letter
     */
    static Letter letter(Party seller, String body) {
        String signer = signer(seller);
        if (body == null || body.isBlank()) {
            return new Letter(List.of(), List.of(signer));
        }
        List<List<String>> text = paragraphs(new GermanFormat().signed(body, signer));
        if (text.size() > 1) {
            return new Letter(text.subList(0, text.size() - 1), text.getLast());
        }
        return new Letter(text, List.of());
    }

    /** Who signs: the contact person, or the name without one, as on the PDF. */
    static String signer(Party seller) {
        return seller.contactName() == null ? seller.name() : seller.contactName();
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
        GermanFormat format = new GermanFormat();
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

    /** The facts of a reminder: date, due date, the open amount and the bank account. */
    static List<Fact> reminderFacts(InvoiceDocument document, BigDecimal open) {
        GermanFormat format = new GermanFormat();
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("Rechnungsdatum", DATE.format(document.issueDate())));
        facts.add(new Fact("Fällig am", DATE.format(document.dueDate())));
        facts.add(new Fact("Offener Betrag", format.money(open)));
        facts.add(new Fact("IBAN", iban(document.iban())));
        if (!document.bic().isBlank()) {
            facts.add(new Fact("BIC", document.bic()));
        }
        facts.add(new Fact("Verwendungszweck", document.number()));
        return facts;
    }

    /**
     * Returns the text part of the e-mail: the text as typed, the name on the line after the greeting, and below the
     * address as in the HTML part.
     *
     * @param document the invoice
     * @param body the text of the e-mail
     * @return the signed text with the address
     */
    public static String plain(InvoiceDocument document, String body) {
        return new GermanFormat().signed(body, signer(document.seller())) + "\n\n"
                + String.join("\n", contact(document.seller()));
    }

    /**
     * The lines at the foot: address, e-mail and phone, the empty ones left out. The name signs above them; a business
     * name that differs from the one who signs heads the foot.
     */
    static List<String> contact(Party seller) {
        List<String> lines = new ArrayList<>();
        if (!seller.name().equals(signer(seller))) {
            lines.add(seller.name());
        }
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
