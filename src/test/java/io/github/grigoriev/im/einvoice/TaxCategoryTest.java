package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaxCategoryTest {

    @ParameterizedTest
    @CsvSource({
        "KLEINUNTERNEHMER, E, true",
        "EXEMPT_4_21, E, false",
        "REVERSE_CHARGE_EU, AE, false",
        "NOT_TAXABLE_ABROAD, O, false"
    })
    void mapsToEn16931CodeAndThresholdRule(TaxCategory category, String code, boolean counts) {
        assertThat(category.code()).isEqualTo(code);
        assertThat(category.countsForThreshold()).isEqualTo(counts);
    }

    @Test
    void defaultNotesNameTheLegalBasis() {
        assertThat(TaxCategory.KLEINUNTERNEHMER.defaultNote()).isEqualTo("Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.");
        assertThat(TaxCategory.EXEMPT_4_21.defaultNote()).isEqualTo("Steuerfrei gemäß § 4 Nr. 21 UStG.");
        assertThat(TaxCategory.REVERSE_CHARGE_EU.defaultNote()).isEqualTo("Steuerschuldnerschaft des Leistungsempfängers.");
        assertThat(TaxCategory.NOT_TAXABLE_ABROAD.defaultNote()).isEqualTo("Nicht im Inland steuerbare Leistung.");
    }
}
