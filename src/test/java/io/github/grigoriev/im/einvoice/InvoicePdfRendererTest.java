package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer(SampleInvoices.templates());
    private final InvoicePdfRenderer.Formatter fmt = new InvoicePdfRenderer.Formatter();

    @Test
    void rendersAPdfA3bWithEmbeddedFonts() throws IOException {
        byte[] pdf = renderer.render(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertThat(xmp).contains("<pdfaid:part>3</pdfaid:part>").contains("<pdfaid:conformance>B</pdfaid:conformance>");
            assertThat(document.getDocumentCatalog().getOutputIntents()).isNotEmpty();
            for (var name : document.getPage(0).getResources().getFontNames()) {
                PDFont font = document.getPage(0).getResources().getFont(name);
                assertThat(font.isEmbedded()).as(font.getName()).isTrue();
                assertThat(font.getName()).contains("Inter");
            }
        }
    }

    @Test
    void printsTheInvoiceContent() throws IOException {
        String text = text(renderer.render(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931)));
        assertThat(text)
                .contains("Rechnung")
                .contains("2027-10")
                .contains("Verlag am Fluss GmbH")
                .contains("Lesung aus „Der stille Hafen“ am 16.03.2027, 90 Min.")
                .contains("400,00\u00a0€")
                .contains("480,00\u00a0€")
                .contains("Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.")
                .contains("DE02 1203 0000 0000 2020 51")
                .contains("Bitte überweisen Sie den Betrag bis zum 01.04.2027.")
                .contains("vielen Dank für Ihren Auftrag.")
                .contains("Mit freundlichen Grüßen")
                .doesNotContain("Käuferreferenz")
                .doesNotContain("USt-IdNr.");
    }

    @Test
    void printsNetVatPerRateAndTotalForASale() throws IOException {
        String text = text(renderer.render(SampleInvoices.sale(InvoiceFormat.ZUGFERD_EN16931)));
        assertThat(text)
                .contains("Leistung Menge Einzelpreis Netto")
                .contains("Fachbuch „E-Rechnung in der Praxis“ 3 24,90\u00a0€ 74,70\u00a0€")
                .contains("Versand 5,90\u00a0€")
                .contains("Nettobetrag 130,50\u00a0€")
                .contains("USt 19\u00a0% auf 55,80\u00a0€ 10,60\u00a0€")
                .contains("USt 7\u00a0% auf 74,70\u00a0€ 5,23\u00a0€")
                .contains("Gesamtbetrag 146,33\u00a0€")
                .contains("USt-IdNr. DE111111111")
                .doesNotContain("UStG");
    }

    @Test
    void printsOnlyTextAndAmountForLumpSums() throws IOException {
        String text = text(renderer.render(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931)));
        assertThat(text).contains("Leistung Betrag").doesNotContain("Menge").doesNotContain("Einzelpreis")
                .doesNotContain("pauschal").contains("Reisekosten 80,00\u00a0€");
    }

    @Test
    void leavesTheQuantityOfALumpSumEmptyNextToCountedLines() throws IOException {
        InvoiceDocument mixed = SampleInvoices.invoice(SampleInvoices.SCHOOL, null, InvoiceFormat.ZUGFERD_EN16931,
                SampleInvoices.line("Deutschkurs B2, je 45 Min.", "16", "45.00", TaxCategory.EXEMPT_4_21),
                SampleInvoices.lump("Lesung", "400.00", TaxCategory.KLEINUNTERNEHMER));
        String text = text(renderer.render(mixed));
        assertThat(text).contains("Leistung Menge Einzelpreis Betrag").contains("Deutschkurs B2, je 45 Min. 16 45,00\u00a0€ 720,00\u00a0€")
                .contains("Lesung 400,00\u00a0€").doesNotContain("pauschal");
    }

    @Test
    void printsForeignBuyerVatIdAndBuyerReference() throws IOException {
        InvoiceDocument dutch = SampleInvoices.invoice(SampleInvoices.SELLER_EU, SampleInvoices.DUTCH, "REF-1",
                InvoiceFormat.XRECHNUNG, SampleInvoices.line("Workshop", "1", "600.00", TaxCategory.REVERSE_CHARGE_EU));
        String text = text(renderer.render(dutch));
        assertThat(text).contains("NL").contains("USt-IdNr. NL123456789B01").contains("Käuferreferenz REF-1")
                .contains("USt-IdNr. DE123456789").contains("Steuernummer 143/123/45678");
    }

    @Test
    void printsTheVatIdInsteadOfAMissingTaxNumber() throws IOException {
        Party vatOnly = new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE",
                "kontakt@sommer.example", null, null, null, "DE123456789");
        InvoiceDocument document = SampleInvoices.invoice(vatOnly, SampleInvoices.PUBLISHER, null,
                InvoiceFormat.ZUGFERD_EN16931, SampleInvoices.line("x", "3", "0.005", TaxCategory.KLEINUNTERNEHMER));
        String text = text(renderer.render(document));
        assertThat(text).contains("USt-IdNr. DE123456789").doesNotContain("Steuernummer").contains("0,005\u00a0€")
                .contains("0,02\u00a0€");
    }

    @Test
    void omitsOptionalTexts() throws IOException {
        InvoiceDocument plain = new InvoiceDocument("7", SampleInvoices.DAY, SampleInvoices.DAY, SampleInvoices.DAY,
                SampleInvoices.DAY, SampleInvoices.SELLER, SampleInvoices.PUBLISHER, null, "DE02120300000000202051",
                "BIC", null, null, null, InvoiceFormat.ZUGFERD_EN16931,
                List.of(SampleInvoices.line("x", "1", "1", TaxCategory.KLEINUNTERNEHMER)));
        String text = text(renderer.render(plain));
        assertThat(text).doesNotContain("vielen Dank").doesNotContain("Mit freundlichen");
    }

    @Test
    void failsOnAMissingFont() {
        InvoicePdfRenderer broken = new InvoicePdfRenderer(SampleInvoices.templates(), List.of("fonts/missing.ttf"));
        InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        assertThatThrownBy(() -> broken.render(document)).isInstanceOf(EInvoiceException.class)
                .hasMessageContaining("Font not found: fonts/missing.ttf");
    }

    @Test
    void failsOnAnUnreadableFont() {
        InvoicePdfRenderer broken = new InvoicePdfRenderer(SampleInvoices.templates(), List.of("fonts/broken.ttf"));
        InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        assertThatThrownBy(() -> broken.render(document)).isInstanceOf(EInvoiceException.class)
                .hasMessageContaining("Cannot load font fonts/broken.ttf");
    }

    @Test
    void formatsGermanNumbersAndDates() {
        assertThat(fmt.money(new BigDecimal("1234.5"))).isEqualTo("1.234,50\u00a0€");
        assertThat(fmt.money(new BigDecimal("0.125"))).isEqualTo("0,13\u00a0€");
        assertThat(fmt.price(new BigDecimal("45"))).isEqualTo("45,00\u00a0€");
        assertThat(fmt.price(new BigDecimal("41.665"))).isEqualTo("41,665\u00a0€");
        assertThat(fmt.price(new BigDecimal("0.12345"))).isEqualTo("0,1235\u00a0€");
        assertThat(fmt.quantity(new BigDecimal("1.50"))).isEqualTo("1,5");
        assertThat(fmt.quantity(new BigDecimal("120"))).isEqualTo("120");
        assertThat(fmt.quantity(new BigDecimal("0.12345"))).isEqualTo("0,1235");
        assertThat(fmt.date(LocalDate.of(2027, 3, 8))).isEqualTo("08.03.2027");
        assertThat(fmt.period(LocalDate.of(2027, 3, 8), LocalDate.of(2027, 3, 8))).isEqualTo("08.03.2027");
        assertThat(fmt.period(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31))).isEqualTo("01.03.2027 bis 31.03.2027");
        assertThat(fmt.iban("DE02120300000000202051")).isEqualTo("DE02 1203 0000 0000 2020 51");
        assertThat(fmt.iban("DE021203")).isEqualTo("DE02 1203");
        assertThat(fmt.percent(new BigDecimal("19"))).isEqualTo("19\u00a0%");
        assertThat(fmt.percent(new BigDecimal("7.00"))).isEqualTo("7\u00a0%");
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
