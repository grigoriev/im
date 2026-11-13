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
        "NOT_TAXABLE_ABROAD, O, false",
        "STANDARD_19, S, false",
        "STANDARD_7, S, false"
    })
    void mapsToEn16931CodeAndThresholdRule(TaxCategory category, String code, boolean counts) {
        assertThat(category.code()).isEqualTo(code);
        assertThat(category.countsForThreshold()).isEqualTo(counts);
    }

    @ParameterizedTest
    @CsvSource({
        "KLEINUNTERNEHMER, 0, false",
        "EXEMPT_4_21, 0, false",
        "REVERSE_CHARGE_EU, 0, false",
        "NOT_TAXABLE_ABROAD, 0, false",
        "STANDARD_19, 19, true",
        "STANDARD_7, 7, true"
    })
    void knowsItsVatRate(TaxCategory category, String rate, boolean charges) {
        assertThat(category.rate()).isEqualByComparingTo(rate);
        assertThat(category.chargesVat()).isEqualTo(charges);
    }

    @ParameterizedTest
    @CsvSource({
        "KLEINUNTERNEHMER, § 19",
        "EXEMPT_4_21, § 4 Nr. 21",
        "REVERSE_CHARGE_EU, Reverse Charge",
        "NOT_TAXABLE_ABROAD, nicht steuerbar"
    })
    void labelsTheTaxColumn(TaxCategory category, String label) {
        assertThat(category.label()).isEqualTo(label);
    }

    @Test
    void labelsTheStandardRatesWithTheirPercentage() {
        assertThat(TaxCategory.STANDARD_19.label()).isEqualTo("19\u00a0%");
        assertThat(TaxCategory.STANDARD_7.label()).isEqualTo("7\u00a0%");
    }

    @Test
    void defaultNotesNameTheLegalBasis() {
        assertThat(TaxCategory.KLEINUNTERNEHMER.defaultNote()).isEqualTo("Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.");
        assertThat(TaxCategory.EXEMPT_4_21.defaultNote()).isEqualTo("Steuerfrei gemäß § 4 Nr. 21 UStG.");
        assertThat(TaxCategory.REVERSE_CHARGE_EU.defaultNote()).isEqualTo("Steuerschuldnerschaft des Leistungsempfängers.");
        assertThat(TaxCategory.NOT_TAXABLE_ABROAD.defaultNote()).isEqualTo("Nicht im Inland steuerbare Leistung.");
        assertThat(TaxCategory.STANDARD_19.defaultNote()).isEmpty();
        assertThat(TaxCategory.STANDARD_7.defaultNote()).isEmpty();
    }
}
