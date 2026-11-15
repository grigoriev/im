package io.github.grigoriev.im.mail;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.Party;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailHtmlTest {

    private final MailHtml html = new MailHtml(SampleInvoices.templates());

    private static InvoiceDocument withBank(InvoiceDocument d, String bic) {
        return new InvoiceDocument(d.number(), d.issueDate(), d.serviceFrom(), d.serviceTo(), d.dueDate(), d.seller(),
                d.buyer(), d.buyerReference(), d.iban(), bic, d.intro(), d.closing(), d.taxNote(), d.format(), d.lines(),
                d.cancels());
    }

    @Test
    void writesTheTextTheFactsAndTheAddress() {
        String page = html.render(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931), "Rechnung 2027-10",
                """
                Sehr geehrte Damen und Herren,\r
                \r
                anbei die Rechnung <b>2027-10</b>.
                Bitte bis Ende März.


                Mit freundlichen Grüßen
                Elena Sommer
                """);
        assertThat(page).contains("color:#1f2937;overflow-wrap:anywhere;\">").contains("<title>Rechnung 2027-10</title>")
                .contains("<p style=\"margin:0 0 14px 0;\"><span>Sehr geehrte Damen und Herren,</span></p>")
                .as("the text stays text").contains("<span>anbei die Rechnung &lt;b&gt;2027-10&lt;/b&gt;.</span><br>"
                        + "<span>Bitte bis Ende März.</span></p>")
                .as("the greeting with the name below the box, the name once")
                .containsPattern("Verwendungszweck[\\s\\S]*<p style=\"margin:0;\"><span>Mit freundlichen Grüßen</span><br>"
                        + "<span>Elena Sommer</span></p>")
                .contains(">Rechnung 2027-10</td>")
                .contains(">Rechnungsdatum</td>").contains(">18.03.2027</td>")
                .contains(">Betrag</td>").contains(">480,00\u00a0€</td>")
                .contains(">Zahlbar bis</td>").contains(">01.04.2027</td>")
                .contains(">DE02 1203 0000 0000 2020 51</td>")
                .contains(">BYLADEM1001</td>")
                .contains(">Verwendungszweck</td>")
                .contains("<span>Lindenstraße 12</span><br><span>80331 München</span><br>"
                        + "<span>kontakt@sommer.example · +49 89 1234567</span>")
                .doesNotContain("Storno zu").doesNotContain("<script");
    }

    @Test
    void aStornorechnungNamesItsInvoiceAndAsksForNoPayment() {
        String page = html.render(SampleInvoices.cancellation(InvoiceFormat.ZUGFERD_EN16931), null, null);
        assertThat(page).contains("<title></title>").contains(">Stornorechnung 2027-10</td>")
                .contains(">Storno zu</td>").contains(">Rechnung 2027-07 vom 08.03.2027</td>")
                .doesNotContain("Zahlbar bis").doesNotContain("IBAN").doesNotContain("Verwendungszweck")
                .as("no text, but the name signs").doesNotContain("margin:0 0 14px 0")
                .contains("<p style=\"margin:0;\"><span>Elena Sommer</span></p>");
    }

    @Test
    void leavesOutAnEmptyBicAndAnEmptyContact() {
        InvoiceDocument reading = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        assertThat(MailHtml.facts(withBank(reading, " "))).extracting(MailHtml.Fact::label)
                .containsExactly("Rechnungsdatum", "Betrag", "Zahlbar bis", "IBAN", "Verwendungszweck");
        assertThat(MailHtml.facts(reading)).contains(new MailHtml.Fact("BIC", "BYLADEM1001"),
                new MailHtml.Fact("Verwendungszweck", "2027-10"));
        assertThat(MailHtml.contact(new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE", null, "",
                null, null, null))).containsExactly("Lindenstraße 12", "80331 München");
        assertThat(MailHtml.contact(new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE", "",
                "+49 89 1", null, null, null))).endsWith("+49 89 1");
    }

    @Test
    void signsBelowTheGreetingOnce() {
        Party seller = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931).seller();
        assertThat(MailHtml.letter(seller, "Hallo,\n\nanbei.\n\nViele Grüße")).isEqualTo(new MailHtml.Letter(
                List.of(List.of("Hallo,"), List.of("anbei.")), List.of("Viele Grüße", "Elena Sommer")));
        assertThat(MailHtml.letter(seller, "Hallo,\n\nViele Grüße\nElena Sommer").closing())
                .as("a text that ends with the name").containsExactly("Viele Grüße", "Elena Sommer");
        assertThat(MailHtml.letter(seller, "Anbei die Rechnung.")).as("one paragraph, signed above the box")
                .isEqualTo(new MailHtml.Letter(List.of(List.of("Anbei die Rechnung.", "Elena Sommer")), List.of()));
        assertThat(MailHtml.letter(seller, null)).isEqualTo(new MailHtml.Letter(List.of(), List.of("Elena Sommer")));
        assertThat(MailHtml.letter(seller, " ")).isEqualTo(new MailHtml.Letter(List.of(), List.of("Elena Sommer")));
        assertThat(MailHtml.letter(seller, "Hallo,\n\nanbei.\n\nMit freundlichen Grüßen\n\nElena Sommer"))
                .as("a name in a paragraph of its own joins the greeting").isEqualTo(new MailHtml.Letter(
                        List.of(List.of("Hallo,"), List.of("anbei.")), List.of("Mit freundlichen Grüßen", "Elena Sommer")));
        Party company = new Party("Sommer Sprachen GmbH", "Lindenstraße 12", "80331", "München", "DE", null, null,
                "Elena Sommer", null, null);
        Party unnamed = new Party("Sommer Sprachen GmbH", "Lindenstraße 12", "80331", "München", "DE", null, null,
                null, null, null);
        assertThat(MailHtml.signer(company)).as("the contact person signs").isEqualTo("Elena Sommer");
        assertThat(MailHtml.contact(company)).as("the business heads the foot").startsWith("Sommer Sprachen GmbH",
                "Lindenstraße 12");
        assertThat(MailHtml.contact(unnamed)).as("no second line of the same name").startsWith("Lindenstraße 12");
        assertThat(MailHtml.signer(unnamed)).isEqualTo("Sommer Sprachen GmbH");
        InvoiceDocument reading = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        assertThat(MailHtml.plain(reading, " Hallo,\n\nMit freundlichen Grüßen\n")).isEqualTo("Hallo,\n\n"
                + "Mit freundlichen Grüßen\nElena Sommer\n\nLindenstraße 12\n80331 München\n"
                + "kontakt@sommer.example · +49 89 1234567");
        assertThat(MailHtml.plain(reading, "Gruß\nElena Sommer")).startsWith("Gruß\nElena Sommer\n\nLindenstraße");
    }

    @Test
    void splitsTheTextIntoParagraphsAndLines() {
        assertThat(MailHtml.paragraphs(null)).isEmpty();
        assertThat(MailHtml.paragraphs(" \n ")).isEmpty();
        assertThat(MailHtml.paragraphs("  Hallo  \n Welt\n \nTschüss")).containsExactly(List.of("Hallo", "Welt"),
                List.of("Tschüss"));
    }

    @Test
    void groupsAnIbanByFour() {
        assertThat(MailHtml.iban("DE02120300000000202051")).isEqualTo("DE02 1203 0000 0000 2020 51");
        assertThat(MailHtml.iban("DE02 1203 0000 0000 2020 51")).isEqualTo("DE02 1203 0000 0000 2020 51");
        assertThat(MailHtml.iban("DE021203")).isEqualTo("DE02 1203");
    }

    @Test
    void headsWithTheKindAndTheNumber() {
        assertThat(MailHtml.heading(SampleInvoices.reading(InvoiceFormat.XRECHNUNG))).isEqualTo("Rechnung 2027-10");
        assertThat(MailHtml.heading(SampleInvoices.cancellation(InvoiceFormat.XRECHNUNG)))
                .isEqualTo("Stornorechnung 2027-10");
    }
}
