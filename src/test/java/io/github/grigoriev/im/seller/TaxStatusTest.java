package io.github.grigoriev.im.seller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaxStatusTest {

    @Test
    void labelsTheStatusInGerman() {
        assertThat(TaxStatus.KLEINUNTERNEHMER.label()).isEqualTo("Kleinunternehmer nach § 19 UStG");
        assertThat(TaxStatus.REGULAR.label()).isEqualTo("Regelbesteuerung (19 % und 7 %)");
    }
}
