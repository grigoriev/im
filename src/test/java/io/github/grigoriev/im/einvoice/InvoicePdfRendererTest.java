package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.format.GermanFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer(SampleInvoices.templates());

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
                assertThat(font.getName()).contains("JetBrainsMonoNL");
            }
        }
    }

    @Test
    void printsAStornorechnungWithItsInvoice() throws IOException {
        String text = text(renderer.render(SampleInvoices.cancellation(InvoiceFormat.ZUGFERD_EN16931)));
        assertThat(text)
                .contains("Stornorechnung")
                .contains("Storno zu 2027-07 vom 08.03.2027")
                .contains("Diese Stornorechnung hebt die Rechnung 2027-07 vom 08.03.2027 vollständig auf.")
                .contains("480,00\u00a0€")
                .doesNotContain("Bitte überweisen");
        assertThat(text(renderer.render(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931))))
                .doesNotContain("Storno");
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
                .contains("Für diese Leistung gilt die Steuerbefreiung für Kleinunternehmer (§ 19 Abs. 1")
                .contains("DE02 1203 0000 0000 2020 51")
                .doesNotContain("Tel.")
                .contains("Bitte überweisen Sie den Rechnungsbetrag von 480,00\u00a0€ bis zum 01.04.2027.")
                .contains("vielen Dank für Ihren Auftrag.")
                .contains("Mit freundlichen Grüßen\nElena Sommer\n")
                .doesNotContain("Käuferreferenz")
                .doesNotContain("USt-IdNr.");
    }

    @Test
    void printsNetVatPerRateAndTotalForASale() throws IOException {
        String text = text(renderer.render(SampleInvoices.sale(InvoiceFormat.ZUGFERD_EN16931)));
        assertThat(text)
                .contains("Leistung Menge Einzelpreis USt Netto")
                .contains("Fachbuch „E-Rechnung in der Praxis“ 3 24,90\u00a0€ 7\u00a0% 74,70\u00a0€")
                .contains("Versand 19\u00a0% 5,90\u00a0€")
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
        assertThat(text).contains("Leistung Betrag").doesNotContain("Menge").doesNotContain("Einzelpreis").doesNotContain("USt ")
                .doesNotContain("pauschal").contains("Reisekosten 80,00\u00a0€");
    }

    @Test
    void leavesTheQuantityOfALumpSumEmptyNextToCountedLines() throws IOException {
        InvoiceDocument mixed = SampleInvoices.invoice(SampleInvoices.SCHOOL, null, InvoiceFormat.ZUGFERD_EN16931,
                SampleInvoices.line("Deutschkurs B2, je 45 Min.", "16", "45.00", TaxCategory.EXEMPT_4_21),
                SampleInvoices.lump("Lesung", "400.00", TaxCategory.KLEINUNTERNEHMER));
        String text = text(renderer.render(mixed));
        assertThat(text).contains("Leistung Menge Einzelpreis USt Betrag")
                .contains("Deutschkurs B2, je 45 Min. 16 45,00\u00a0€ § 4 Nr. 21 720,00\u00a0€")
                .contains("Lesung § 19 400,00\u00a0€").doesNotContain("pauschal");
    }

    @Test
    void printsForeignBuyerVatIdAndBuyerReference() throws IOException {
        InvoiceDocument dutch = SampleInvoices.invoice(SampleInvoices.SELLER_EU, SampleInvoices.DUTCH, "REF-1",
                InvoiceFormat.XRECHNUNG, SampleInvoices.line("Workshop", "1", "600.00", TaxCategory.REVERSE_CHARGE_EU));
        String text = text(renderer.render(dutch));
        assertThat(text).contains("NIEDERLANDE").contains("Ihre USt-IdNr. NL123456789B01")
                .contains("Käuferreferenz REF-1").contains("USt-IdNr. DE123456789").contains("Steuernummer 143/123/45678");
    }

    @Test
    void printsTheReferencesOfTheCustomerInTheInfoBox() throws IOException {
        String text = text(renderer.render(SampleInvoices.withReferences(
                SampleInvoices.department(InvoiceFormat.ZUGFERD_EN16931), new CustomerReferences("K-17", "4500012345",
                        "V-2027-01"))));
        assertThat(text).as("a short value keeps the label on one line").contains("Geschäftspartnernummer K-17\n")
                .contains("Bestellnummer 4500012345\n").contains("Vertrag V-2027-01\n");
        String fits = "123456789012345";
        assertThat(text(renderer.render(SampleInvoices.withReferences(
                SampleInvoices.department(InvoiceFormat.ZUGFERD_EN16931), new CustomerReferences(fits, null, null)))))
                .as("the longest value beside the whole label").contains("Geschäftspartnernummer " + fits + "\n");
        assertThat(text(renderer.render(SampleInvoices.department(InvoiceFormat.ZUGFERD_EN16931))))
                .doesNotContain("Geschäftspartnernummer").doesNotContain("Bestellnummer").doesNotContain("Vertrag");
    }

    static Stream<Arguments> references() {
        String longest = "Rahmenvertrag ".repeat(15).substring(0, 200);
        String word = "4500012345".repeat(20);
        return Stream.of(
                Arguments.of("sample", SampleInvoices.REFERENCES),
                Arguments.of("longest texts", new CustomerReferences(longest, longest, longest)),
                Arguments.of("longest words", new CustomerReferences(word, word, word)));
    }

    /** Long references wrap inside the info box: right of the address field and inside the right page margin. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("references")
    void theReferencesWrapInsideTheInfoBox(String title, CustomerReferences references) throws IOException {
        byte[] rendered = renderer.render(SampleInvoices.withReferences(
                SampleInvoices.department(InvoiceFormat.ZUGFERD_EN16931), references));
        float mm = 72 / 25.4f;
        List<Float> rightEdges = new ArrayList<>();
        Map<String, Float> labels = new HashMap<>();
        try (PDDocument pdf = Loader.loadPDF(rendered)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String line, List<TextPosition> positions) {
                    positions.forEach(glyph -> rightEdges.add(glyph.getXDirAdj() + glyph.getWidthDirAdj()));
                    for (String label : List.of("Geschäftspartner", "Bestellnummer", "Vertrag", "Rechnungsnummer")) {
                        if (line.startsWith(label)) {
                            labels.put(label, positions.getFirst().getXDirAdj());
                        }
                    }
                }
            };
            stripper.setEndPage(1);
            stripper.getText(pdf);
        }
        assertThat(labels).as("every row of the info box").containsKeys("Geschäftspartner", "Bestellnummer",
                "Vertrag", "Rechnungsnummer");
        assertThat(labels.values()).as("right of the address field").allMatch(x -> x >= 107 * mm);
        assertThat(rightEdges).as("inside the right margin").allMatch(x -> x <= 190 * mm + 0.5f);
        String text = text(rendered);
        assertThat(text.replaceAll("\\s+", "")).as("the values in full")
                .contains("Bestellnummer" + references.purchaseOrder().replace(" ", ""))
                .contains("Vertrag" + references.contract().replace(" ", ""));
        assertThat(text).as("the long label breaks at its hyphen").contains("Geschäftspartner-");
    }

    @Test
    void showsThePhoneNumberWhenTheProfileAsks() throws IOException {
        InvoiceDocument d = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        InvoiceDocument withPhone = new InvoiceDocument(d.number(), d.issueDate(), d.serviceFrom(), d.serviceTo(),
                d.dueDate(), d.seller(), d.buyer(), d.buyerReference(), d.iban(), d.bic(), d.intro(), d.closing(),
                d.taxNote(), d.format(), d.lines(), d.cancels(), true);
        assertThat(text(renderer.render(withPhone))).contains("kontakt@sommer.example\nTel. +49 89 1234567\nSteuernummer");
    }

    @Test
    void signsOnceBelowTheClosing() throws IOException {
        InvoiceDocument d = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        InvoiceDocument signed = new InvoiceDocument(d.number(), d.issueDate(), d.serviceFrom(), d.serviceTo(),
                d.dueDate(), d.seller(), d.buyer(), d.buyerReference(), d.iban(), d.bic(), d.intro(),
                "Mit freundlichen Grüßen\nElena Sommer ", d.taxNote(), d.format(), d.lines());
        assertThat(text(renderer.render(signed))).as("a closing that ends with the name gets no second one")
                .containsPattern("Grüßen\nElena Sommer ?\nElektronische").doesNotContain("Elena Sommer\nElena Sommer");
        Party company = new Party("Sommer Sprachen GmbH", "Lindenstraße 12", "80331", "München", "DE",
                "kontakt@sommer.example", null, null, "143/123/45678", null);
        InvoiceDocument unnamed = new InvoiceDocument(d.number(), d.issueDate(), d.serviceFrom(), d.serviceTo(),
                d.dueDate(), company, d.buyer(), d.buyerReference(), d.iban(), d.bic(), d.intro(), d.closing(),
                d.taxNote(), d.format(), d.lines());
        assertThat(text(renderer.render(unnamed))).as("without a contact person the name signs")
                .contains("Grüßen\nSommer Sprachen GmbH\n");
    }

    @Test
    void namesTheXmlAsTheInvoice() throws IOException {
        String zugferd = "Elektronische Rechnung im Format ZUGFeRD. Diese PDF-Datei enthält die Rechnungsdaten als XML; "
                + "maßgeblich sind die XML-Daten.";
        String copy = "Lesefassung der elektronischen Rechnung im Format XRechnung. Maßgeblich ist die XML-Datei.";
        for (InvoiceFormat format : List.of(InvoiceFormat.ZUGFERD_EN16931, InvoiceFormat.ZUGFERD_XRECHNUNG)) {
            assertThat(text(renderer.render(SampleInvoices.reading(format))).replaceAll("\\s+", " ")).contains(zugferd)
                    .doesNotContain("Lesefassung");
        }
        assertThat(text(renderer.render(SampleInvoices.reading(InvoiceFormat.XRECHNUNG))).replaceAll("\\s+", " "))
                .contains(copy).doesNotContain("Format ZUGFeRD");
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
    void printsTheAddressAdditionAndTheContactBelowTheName() throws IOException {
        assertThat(text(renderer.render(SampleInvoices.department(InvoiceFormat.ZUGFERD_EN16931))))
                .contains("Sprachschule am See\nKST 80441000, Bereich 44 DaF-Programme\nFrau Weber\nSeeweg 5\n86150 Musterstadt\n")
                .doesNotContain("z. Hd.");
        Party b = SampleInvoices.PUBLISHER;
        Party addition = new Party(b.name(), "Buchhaltung", b.street(), b.postcode(), b.city(), b.countryCode(),
                b.email(), null, null, null, null);
        InvoiceDocument document = SampleInvoices.invoice(addition, null, InvoiceFormat.ZUGFERD_EN16931,
                SampleInvoices.lump("Lesung", "400.00", TaxCategory.KLEINUNTERNEHMER));
        assertThat(text(renderer.render(document))).contains("Verlag am Fluss GmbH\nBuchhaltung\nHafenweg 3\n20457 Hamburg\n");
    }

    @Test
    void printsAForeignCountryInCapitals() throws IOException {
        InvoiceDocument greek = SampleInvoices.invoice(SampleInvoices.GREEK, null, InvoiceFormat.ZUGFERD_EN16931,
                SampleInvoices.lump("Lesung", "350.00", TaxCategory.NOT_TAXABLE_ABROAD));
        assertThat(text(renderer.render(greek))).contains("10563 Athen\nGRIECHENLAND\n").doesNotContain("Griechenland");
    }

    @Test
    void omitsOptionalTexts() throws IOException {
        InvoiceDocument plain = new InvoiceDocument("7", SampleInvoices.DAY, SampleInvoices.DAY, SampleInvoices.DAY,
                SampleInvoices.DAY, SampleInvoices.SELLER, SampleInvoices.PUBLISHER, null, "DE02120300000000202051",
                "BIC", null, null, null, InvoiceFormat.ZUGFERD_EN16931,
                List.of(SampleInvoices.line("x", "1", "1", TaxCategory.KLEINUNTERNEHMER)));
        String text = text(renderer.render(plain));
        assertThat(text).doesNotContain("vielen Dank").doesNotContain("Mit freundlichen")
                .as("no closing, no signature").doesNotContain("München\nElena Sommer\n");
    }

    static Stream<Arguments> addresses() {
        String longName = "Gemeinnützige Gesellschaft für Sprachförderung und interkulturelle Bildung Musterstadt mbH";
        String longest = "Gemeinnützige Gesellschaft ".repeat(8).substring(0, 200).strip();
        return Stream.of(
                Arguments.of("six lines", "Sprachschule am See", "KST 80441000, Bereich 44 DaF-Programme", "Frau Weber",
                        "AT"),
                Arguments.of("longest addition and contact", "Sprachschule am See", "Bereich ".repeat(25).substring(0, 200),
                        "Frau Weber ".repeat(19).substring(0, 200), "AT"),
                Arguments.of("long name alone", longName, null, null, "DE"),
                Arguments.of("long name, addition, contact, abroad", longName, "Abrechnungen Online-Sprachkurse",
                        "Frau Weber", "AT"),
                Arguments.of("longest of all", longest, "Bereich ".repeat(25).substring(0, 200),
                        "Frau Weber ".repeat(19).substring(0, 200), "AT"));
    }

    /**
     * DIN 5008, form B: the address field ends 90 mm from the top edge, whatever the buyer holds. A long name stays in
     * full, as §14 Abs. 4 Nr. 1 UStG asks; the addition and the contact person keep one line each.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("addresses")
    void theAddressStaysInsideTheAddressField(String title, String name, String addition, String contact, String country)
            throws IOException {
        Party b = SampleInvoices.DEPARTMENT;
        Party buyer = new Party(name, addition, b.street(), "6020", "Innsbruck", country, b.email(), null, contact, null,
                null);
        InvoiceDocument document = SampleInvoices.invoice(buyer, null, InvoiceFormat.ZUGFERD_EN16931,
                SampleInvoices.line("Online-Sprachkurs Deutsch B1, je 45 Min.", "24", "41.00", TaxCategory.NOT_TAXABLE_ABROAD));
        byte[] rendered = renderer.render(document);
        Map<String, Float> bottoms = new HashMap<>();
        try (PDDocument pdf = Loader.loadPDF(rendered)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String line, List<TextPosition> positions) {
                    TextPosition last = positions.getLast();
                    bottoms.put(line.strip(), last.getYDirAdj() + last.getFont().getFontDescriptor().getDescent() / 1000
                            * -last.getFontSizeInPt());
                }
            };
            stripper.getText(pdf);
        }
        String lastLine = "DE".equals(country) ? "6020 Innsbruck" : "ÖSTERREICH";
        assertThat(bottoms).as("the stripper hands over whole lines").containsKey(lastLine);
        float mm = 72 / 25.4f;
        assertThat(bottoms.get(lastLine)).as("bottom of the last line").isLessThanOrEqualTo(90 * mm);
        float first = bottoms.entrySet().stream().filter(line -> name.startsWith(line.getKey()))
                .map(Map.Entry::getValue).min(Float::compare).orElseThrow();
        assertThat(first).as("the first line below the sender line").isGreaterThan(62.7f * mm);
        assertThat(text(rendered).replaceAll("\\s+", " ")).as("the full name").contains(name);
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
    void printsAsManyDecimalsAsTheXmlCarries() {
        assertThat(GermanFormat.DECIMALS).isEqualTo(InvoiceLine.MAX_DECIMALS);
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
