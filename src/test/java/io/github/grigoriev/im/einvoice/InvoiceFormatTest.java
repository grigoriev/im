package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceFormatTest {

    @ParameterizedTest
    @CsvSource({
        "ZUGFERD_EN16931, true, false",
        "ZUGFERD_XRECHNUNG, true, true",
        "XRECHNUNG, false, true"
    })
    void describesPdfAndXRechnungRules(InvoiceFormat format, boolean pdf, boolean xrechnung) {
        assertThat(format.isPdf()).isEqualTo(pdf);
        assertThat(format.followsXRechnung()).isEqualTo(xrechnung);
    }
}
