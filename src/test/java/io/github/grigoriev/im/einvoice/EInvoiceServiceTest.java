package io.github.grigoriev.im.einvoice;

import static io.github.grigoriev.im.einvoice.SampleInvoices.DUTCH;
import static io.github.grigoriev.im.einvoice.SampleInvoices.PUBLISHER;
import static io.github.grigoriev.im.einvoice.SampleInvoices.SCHOOL;
import static io.github.grigoriev.im.einvoice.SampleInvoices.SELLER_EU;
import static io.github.grigoriev.im.einvoice.SampleInvoices.SWISS;
import static io.github.grigoriev.im.einvoice.SampleInvoices.invoice;
import static io.github.grigoriev.im.einvoice.SampleInvoices.line;
import static io.github.grigoriev.im.einvoice.TaxCategory.EXEMPT_4_21;
import static io.github.grigoriev.im.einvoice.TaxCategory.KLEINUNTERNEHMER;
import static io.github.grigoriev.im.einvoice.TaxCategory.NOT_TAXABLE_ABROAD;
import static io.github.grigoriev.im.einvoice.TaxCategory.REVERSE_CHARGE_EU;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the full chain for every format and every allowed mix of categories, see section 10 of
 * {@code docs/PLAN.md}: render, write, validate, read the XML back.
 */
class EInvoiceServiceTest {

    private static final String REF = "04011000-12345-67";

    private final EInvoiceService service = SampleInvoices.service();

    static Stream<Arguments> invoices() {
        List<Arguments> cases = new ArrayList<>();
        for (InvoiceFormat format : InvoiceFormat.values()) {
            String ref = format.followsXRechnung() ? REF : null;
            cases.add(Arguments.of(format, "§19", invoice(PUBLISHER, ref, format, line("Lesung", "1", "400.00", KLEINUNTERNEHMER))));
            cases.add(Arguments.of(format, "§4", invoice(SCHOOL, ref, format, line("Unterricht", "24", "41.00", EXEMPT_4_21))));
            cases.add(Arguments.of(format, "§19+§4", invoice(SCHOOL, ref, format,
                    line("Unterricht", "16", "45.00", EXEMPT_4_21), line("Lesung", "1", "400.00", KLEINUNTERNEHMER))));
            cases.add(Arguments.of(format, "AE", invoice(SELLER_EU, DUTCH, ref, format, line("Workshop", "1", "600.00", REVERSE_CHARGE_EU))));
            cases.add(Arguments.of(format, "lump sums", SampleInvoices.reading(format)));
            cases.add(Arguments.of(format, "sale 19+7", SampleInvoices.sale(format)));
            InvoiceLine cents = line("Kleinteil", "1", "0.03", TaxCategory.STANDARD_19);
            cases.add(Arguments.of(format, "VAT rounded once per rate",
                    invoice(SampleInvoices.SHOP, PUBLISHER, ref, format, cents, cents, cents)));
            cases.add(Arguments.of(format, "19+§4", invoice(SampleInvoices.SHOP, SCHOOL, ref, format,
                    line("Lehrbuch", "2", "19.90", TaxCategory.STANDARD_19),
                    line("Unterricht", "16", "45.00", EXEMPT_4_21))));
            cases.add(Arguments.of(format, "19+AE", invoice(SampleInvoices.SHOP, DUTCH, ref, format,
                    line("Lehrbuch", "2", "19.90", TaxCategory.STANDARD_19),
                    line("Workshop", "1", "600.00", REVERSE_CHARGE_EU))));
            cases.add(Arguments.of(format, "O", invoice(SWISS, ref, format, line("Lesung", "1", "350.00", NOT_TAXABLE_ABROAD))));
            cases.add(Arguments.of(format, "Stornorechnung", SampleInvoices.cancellation(format)));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invoices")
    void issuesAValidEInvoice(InvoiceFormat format, String mix, InvoiceDocument document) {
        IssuedEInvoice issued = service.issue(document);
        assertThat(issued.format()).isEqualTo(format);
        assertThat(issued.pdf()).startsWith("%PDF".getBytes());
        assertThat(issued.xml()).isNotEmpty();
        assertThat(issued.validations()).allMatch(ValidationResult::valid);
        String xml = new String(issued.xml(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(xml).as("Mustang computes the same totals")
                .contains("<ram:GrandTotalAmount>" + document.total().toPlainString() + "</ram:GrandTotalAmount>")
                .contains("<ram:TaxBasisTotalAmount>" + document.netTotal().toPlainString() + "</ram:TaxBasisTotalAmount>");
        if (document.isCancellation()) {
            assertThat(xml).as("a credit note that names the cancelled invoice")
                    .contains("<ram:TypeCode>381</ram:TypeCode>")
                    .containsPattern("<ram:InvoiceReferencedDocument>\\s*<ram:IssuerAssignedID>2027-07</ram:IssuerAssignedID>")
                    .contains("20270308");
        } else {
            assertThat(xml).contains("<ram:TypeCode>380</ram:TypeCode>").doesNotContain("InvoiceReferencedDocument");
        }
        List<String> validators = issued.validations().stream().map(ValidationResult::validator).toList();
        switch (format) {
            case ZUGFERD_EN16931 -> assertThat(validators).containsExactly("Mustang");
            case ZUGFERD_XRECHNUNG -> assertThat(validators).containsExactly("Mustang", "KoSIT");
            case XRECHNUNG -> assertThat(validators).containsExactly("KoSIT");
        }
    }

    @Test
    void refusesAnInvoiceThatFailsValidation() {
        ZugferdValidator failing = mock(ZugferdValidator.class);
        when(failing.validate(any())).thenReturn(new ValidationResult(false, "Mustang", List.of("BR-1", "BR-2")));
        XRechnungValidator alsoFailing = mock(XRechnungValidator.class);
        when(alsoFailing.validate(any())).thenReturn(new ValidationResult(false, "KoSIT", List.of("BR-DE-1")));
        EInvoiceService strict = new EInvoiceService(new InvoicePdfRenderer(SampleInvoices.templates()),
                new EInvoiceWriter(new MustangInvoiceMapper()), failing, alsoFailing);
        InvoiceDocument document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_XRECHNUNG);
        assertThatThrownBy(() -> strict.issue(document)).isInstanceOf(EInvoiceException.class)
                .hasMessage("Invoice 2027-10 failed validation: Mustang BR-1; BR-2 | KoSIT BR-DE-1");
    }

}
