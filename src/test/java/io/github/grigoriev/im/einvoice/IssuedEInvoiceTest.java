package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IssuedEInvoiceTest {

    private static final List<ValidationResult> OK = List.of(new ValidationResult(true, "KoSIT", List.of()));

    private static IssuedEInvoice issued(InvoiceFormat format, String pdf, String xml, List<ValidationResult> results) {
        return new IssuedEInvoice(format, pdf.getBytes(), xml.getBytes(), results);
    }

    @Test
    void comparesTheContentOfTheFiles() {
        IssuedEInvoice one = issued(InvoiceFormat.XRECHNUNG, "pdf", "xml", OK);
        assertThat(one)
                .isEqualTo(issued(InvoiceFormat.XRECHNUNG, "pdf", "xml", OK))
                .isEqualTo(one)
                .hasSameHashCodeAs(issued(InvoiceFormat.XRECHNUNG, "pdf", "xml", OK))
                .isNotEqualTo(issued(InvoiceFormat.ZUGFERD_EN16931, "pdf", "xml", OK))
                .isNotEqualTo(issued(InvoiceFormat.XRECHNUNG, "other", "xml", OK))
                .isNotEqualTo(issued(InvoiceFormat.XRECHNUNG, "pdf", "other", OK))
                .isNotEqualTo(issued(InvoiceFormat.XRECHNUNG, "pdf", "xml", List.of()));
        assertThat(one.equals(null)).isFalse();
    }

    @Test
    void namesTheSizesInsteadOfTheBytes() {
        assertThat(issued(InvoiceFormat.XRECHNUNG, "pdf!", "xm", List.of()))
                .hasToString("IssuedEInvoice[format=XRECHNUNG, pdf=4 bytes, xml=2 bytes, validations=[]]");
    }

    @Test
    void protectsItsFilesFromChanges() {
        byte[] pdf = "pdf".getBytes();
        IssuedEInvoice issued = new IssuedEInvoice(InvoiceFormat.ZUGFERD_EN16931, pdf, "xml".getBytes(), OK);
        pdf[0] = 'x';
        issued.pdf()[1] = 'x';
        issued.xml()[0] = 'y';
        assertThat(issued.pdf()).isEqualTo("pdf".getBytes());
        assertThat(issued.xml()).isEqualTo("xml".getBytes());
    }

    @Test
    void copiesItsValidations() {
        List<ValidationResult> results = new ArrayList<>();
        IssuedEInvoice issued = issued(InvoiceFormat.XRECHNUNG, "", "", results);
        results.add(new ValidationResult(true, "KoSIT", List.of()));
        assertThat(issued.validations()).isEmpty();
    }
}
