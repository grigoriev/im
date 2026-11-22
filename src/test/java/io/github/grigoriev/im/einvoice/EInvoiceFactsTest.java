package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.einvoice.EInvoiceFacts.Difference;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EInvoiceFactsTest {

    private final EInvoiceWriter writer = new EInvoiceWriter(new MustangInvoiceMapper());
    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer(SampleInvoices.templates());

    private byte[] xml(InvoiceDocument document) {
        return document.format().isPdf() ? writer.embeddedXml(writer.zugferd(document, renderer.render(document)))
                : writer.xrechnung(document);
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceFormat.class, names = "EXTERNAL", mode = EnumSource.Mode.EXCLUDE)
    void readsFromTheXmlWhatTheSnapshotSays(InvoiceFormat format) {
        InvoiceDocument sale = SampleInvoices.sale(format);
        EInvoiceFacts issued = EInvoiceFacts.of(sale);
        assertThat(issued.number()).isEqualTo("2027-10");
        assertThat(issued.total()).isEqualByComparingTo("146.33");
        assertThat(issued.iban()).doesNotContain(" ");
        assertThat(issued.differences(EInvoiceFacts.read(xml(sale)))).isEmpty();
        InvoiceDocument cancellation = SampleInvoices.cancellation(format);
        assertThat(EInvoiceFacts.of(cancellation).differences(EInvoiceFacts.read(xml(cancellation)))).isEmpty();
    }

    @Test
    void namesWhatAChangeAltered() {
        InvoiceDocument sale = SampleInvoices.sale(InvoiceFormat.XRECHNUNG);
        String xml = new String(xml(sale), StandardCharsets.UTF_8);
        String total = EInvoiceFacts.of(sale).total().setScale(2).toPlainString();
        String changed = xml.replace(">" + total + "<", ">1463.30<").replace(">2027-10<", ">2027-99<")
                .replace(EInvoiceFacts.of(sale).iban(), "DE89370400440532013000");
        assertThat(EInvoiceFacts.of(sale).differences(EInvoiceFacts.read(changed.getBytes(StandardCharsets.UTF_8))))
                .containsExactly(new Difference("Rechnungsnummer", "2027-10", "2027-99"),
                        new Difference("Gesamtbetrag", "146,33 €", "1.463,30 €"),
                        new Difference("IBAN", EInvoiceFacts.of(sale).iban(), "DE89370400440532013000"));
    }

    @Test
    void comparesEveryFact() {
        EInvoiceFacts issued = new EInvoiceFacts("1", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 15),
                new BigDecimal("10.00"), "DE1", "A", "B");
        assertThat(issued.differences(new EInvoiceFacts("1", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 15),
                new BigDecimal("10.0"), "DE1", "A", "B"))).as("the same amount at another scale").isEmpty();
        assertThat(issued.differences(new EInvoiceFacts("1", LocalDate.of(2027, 3, 2), LocalDate.of(2027, 3, 16),
                new BigDecimal("10.00"), "DE1", "X", "Y"))).containsExactly(
                new Difference("Rechnungsdatum", "01.03.2027", "02.03.2027"),
                new Difference("Fällig am", "15.03.2027", "16.03.2027"),
                new Difference("Verkäufer", "A", "X"), new Difference("Käufer", "B", "Y"));
        assertThat(issued.differences(new EInvoiceFacts(null, null, null, null, null, null, null))).containsExactly(
                new Difference("Rechnungsnummer", "1", "fehlt"), new Difference("Rechnungsdatum", "01.03.2027", "fehlt"),
                new Difference("Fällig am", "15.03.2027", "fehlt"), new Difference("Gesamtbetrag", "10,00 €", "fehlt"),
                new Difference("IBAN", "DE1", "fehlt"), new Difference("Verkäufer", "A", "fehlt"),
                new Difference("Käufer", "B", "fehlt"));
    }

    @Test
    void readsWhatIsThereAndLeavesOutTheRest() {
        EInvoiceFacts facts = EInvoiceFacts.read(("<rsm:CrossIndustryInvoice xmlns:rsm=\"r\" xmlns:ram=\"a\">"
                + "<rsm:ExchangedDocument><ram:ID> 7 </ram:ID><ram:IssueDateTime><udt:DateTimeString xmlns:udt=\"u\">"
                + "2027-03-01</udt:DateTimeString></ram:IssueDateTime></rsm:ExchangedDocument>"
                + "<ram:SpecifiedTradeSettlementHeaderMonetarySummation><ram:GrandTotalAmount>zehn</ram:GrandTotalAmount>"
                + "</ram:SpecifiedTradeSettlementHeaderMonetarySummation>"
                + "<ram:PayeePartyCreditorFinancialAccount><ram:IBANID>DE12 3456</ram:IBANID>"
                + "</ram:PayeePartyCreditorFinancialAccount></rsm:CrossIndustryInvoice>")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(facts).isEqualTo(new EInvoiceFacts("7", null, null, null, "DE123456", null, null));
        assertThat(EInvoiceFacts.read("<x/>".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(new EInvoiceFacts(null, null, null, null, null, null, null));
    }

    @Test
    void refusesWhatIsNoXmlAndADoctype() {
        byte[] text = "kein XML".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> EInvoiceFacts.read(text))
                .isInstanceOf(EInvoiceException.class).hasMessage("The XML cannot be read");
        byte[] external = ("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>")
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> EInvoiceFacts.read(external)).isInstanceOf(EInvoiceException.class);
        byte[] inner = "<!DOCTYPE x [<!ENTITY e \"boom\">]><x>&e;</x>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> EInvoiceFacts.read(inner)).as("not even an inner entity")
                .isInstanceOf(EInvoiceException.class);
    }
}
