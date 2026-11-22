package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceFormatTest {

    @ParameterizedTest
    @CsvSource({
        "ZUGFERD_EN16931, true, false, false",
        "ZUGFERD_XRECHNUNG, true, true, false",
        "XRECHNUNG, false, true, false",
        "EXTERNAL, false, false, true"
    })
    void describesPdfAndXRechnungRules(InvoiceFormat format, boolean pdf, boolean xrechnung, boolean external) {
        assertThat(format.isPdf()).isEqualTo(pdf);
        assertThat(format.followsXRechnung()).isEqualTo(xrechnung);
        assertThat(format.isExternal()).isEqualTo(external);
    }
}
