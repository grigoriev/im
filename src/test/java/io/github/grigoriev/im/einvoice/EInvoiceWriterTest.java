package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EInvoiceWriterTest {

    private final EInvoiceWriter writer = new EInvoiceWriter(new MustangInvoiceMapper());
    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer(SampleInvoices.templates());

    @Test
    void embedsEn16931XmlIntoThePdf() {
        InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        byte[] pdf = writer.zugferd(document, renderer.render(document));
        String xml = new String(writer.embeddedXml(pdf), StandardCharsets.UTF_8);
        assertThat(xml).contains("urn:cen.eu:en16931:2017").doesNotContain("xrechnung").contains("<ram:ID>2027-10</ram:ID>");
    }

    @Test
    void embedsXRechnungXmlForTheXRechnungProfile() {
        InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_XRECHNUNG);
        byte[] pdf = writer.zugferd(document, renderer.render(document));
        assertThat(new String(writer.embeddedXml(pdf), StandardCharsets.UTF_8))
                .contains("urn:xeinkauf.de:kosit:xrechnung_3.0")
                .contains("<ram:BuyerReference>04011000-12345-67</ram:BuyerReference>");
    }

    @Test
    void writesXRechnungXml() {
        String xml = new String(writer.xrechnung(SampleInvoices.reading(InvoiceFormat.XRECHNUNG)), StandardCharsets.UTF_8);
        assertThat(xml).contains("urn:xeinkauf.de:kosit:xrechnung_3.0").contains("<ram:CategoryCode>E</ram:CategoryCode>").isNotBlank();
    }

    @Test
    void refusesAPdfForPlainXRechnung() {
        InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.XRECHNUNG);
        assertThatIllegalArgumentException().isThrownBy(() -> writer.zugferd(document, new byte[0]))
                .withMessageContaining("has no PDF");
    }

    @Test
    void reportsAnUnreadablePdf() {
        InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        byte[] notAPdf = "not a pdf".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> writer.zugferd(document, notAPdf)).isInstanceOf(EInvoiceException.class)
                .hasMessageContaining("invoice 2027-10").hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    void reportsAPdfWithoutXml() {
        byte[] plain = renderer.render(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        assertThatThrownBy(() -> writer.embeddedXml(plain)).isInstanceOf(EInvoiceException.class)
                .hasMessageContaining("no e-invoice XML");
    }

    @Test
    void picksTheProfileByFormat() {
        assertThat(EInvoiceWriter.profile(InvoiceFormat.ZUGFERD_EN16931).getName()).isEqualTo("EN16931");
        assertThat(EInvoiceWriter.profile(InvoiceFormat.ZUGFERD_XRECHNUNG).getName()).isEqualTo("XRECHNUNG");
        assertThat(EInvoiceWriter.profile(InvoiceFormat.XRECHNUNG).getName()).isEqualTo("XRECHNUNG");
    }
}
