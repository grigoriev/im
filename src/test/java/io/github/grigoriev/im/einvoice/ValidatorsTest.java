package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidatorsTest {

    private static final Path CONFIG = Path.of("target/kosit-xrechnung");

    private final EInvoiceWriter writer = new EInvoiceWriter(new MustangInvoiceMapper());

    @Test
    void mustangAcceptsAValidZugferdPdf() {
        InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        byte[] pdf = writer.zugferd(document, new InvoicePdfRenderer(SampleInvoices.templates()).render(document));
        ValidationResult result = new ZugferdValidator().validate(pdf);
        assertThat(result.valid()).isTrue();
        assertThat(result.validator()).isEqualTo("Mustang");
        assertThat(result.messages()).isEmpty();
    }

    @Test
    void mustangRejectsAPdfWithoutXml() {
        byte[] plain = new InvoicePdfRenderer(SampleInvoices.templates())
                .render(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        ValidationResult result = new ZugferdValidator().validate(plain);
        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).isNotEmpty();
    }

    @Test
    void extractsTheErrorsOfAMustangReport() {
        String report = "<validation><error type=\"1\">first</error><error>\n second \n</error></validation>";
        assertThat(ZugferdValidator.errors(report)).containsExactly("first", "second");
        assertThat(ZugferdValidator.errors("<summary status=\"invalid\"/>")).containsExactly("<summary status=\"invalid\"/>");
    }

    @Test
    void kositAcceptsValidXRechnung() {
        XRechnungValidator validator = new XRechnungValidator(CONFIG);
        ValidationResult result = validator.validate(writer.xrechnung(SampleInvoices.reading(InvoiceFormat.XRECHNUNG)));
        assertThat(result.valid()).isTrue();
        assertThat(result.validator()).isEqualTo("KoSIT");
        assertThat(validator.validate(writer.xrechnung(SampleInvoices.reading(InvoiceFormat.XRECHNUNG))).valid()).isTrue();
    }

    @Test
    void kositReportsFailedRules() {
        String xml = new String(writer.xrechnung(SampleInvoices.reading(InvoiceFormat.XRECHNUNG)), StandardCharsets.UTF_8)
                .replace("<ram:BuyerReference>04011000-12345-67</ram:BuyerReference>", "");
        ValidationResult result = new XRechnungValidator(CONFIG).validate(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).anyMatch(message -> message.startsWith("BR-DE-15"));
    }

    @Test
    void kositReportsSchemaViolations() {
        String xml = new String(writer.xrechnung(SampleInvoices.reading(InvoiceFormat.XRECHNUNG)), StandardCharsets.UTF_8)
                .replace("<ram:TypeCode>380</ram:TypeCode>", "<ram:Unknown>380</ram:Unknown>");
        ValidationResult result = new XRechnungValidator(CONFIG).validate(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).isNotEmpty();
    }

    @Test
    void kositReportsUnreadableInput() {
        ValidationResult result = new XRechnungValidator(CONFIG).validate("no xml".getBytes(StandardCharsets.UTF_8));
        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).isNotEmpty();
    }

    @Test
    void kositNeedsItsConfiguration() {
        XRechnungValidator validator = new XRechnungValidator(Path.of("target/does-not-exist"));
        byte[] xml = "<x/>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> validator.validate(xml)).isInstanceOf(EInvoiceException.class)
                .hasMessageContaining("XRechnung configuration not found");
    }

    @Test
    void validationResultCopiesItsMessages() {
        List<String> messages = new ArrayList<>(List.of("a"));
        ValidationResult result = new ValidationResult(false, "x", messages);
        messages.add("b");
        assertThat(result.messages()).containsExactly("a");
    }
}
