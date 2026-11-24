package io.github.grigoriev.im.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GermanFormatTest {

    private final GermanFormat fmt = new GermanFormat();

    @Test
    void formatsGermanNumbersAndDates() {
        assertThat(fmt.money(new BigDecimal("1234.5"))).isEqualTo("1.234,50 €");
        assertThat(fmt.money(new BigDecimal("0.125"))).isEqualTo("0,13 €");
        assertThat(fmt.price(new BigDecimal("45"))).isEqualTo("45,00 €");
        assertThat(fmt.price(new BigDecimal("41.665"))).isEqualTo("41,665 €");
        assertThat(fmt.price(new BigDecimal("0.12345"))).isEqualTo("0,1235 €");
        assertThat(fmt.quantity(new BigDecimal("1.50"))).isEqualTo("1,5");
        assertThat(fmt.quantity(new BigDecimal("120"))).isEqualTo("120");
        assertThat(fmt.quantity(new BigDecimal("0.12345"))).isEqualTo("0,1235");
        assertThat(fmt.date(LocalDate.of(2027, 3, 8))).isEqualTo("08.03.2027");
        assertThat(fmt.period(LocalDate.of(2027, 3, 8), LocalDate.of(2027, 3, 8))).isEqualTo("08.03.2027");
        assertThat(fmt.period(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31))).isEqualTo("01.03.2027 bis 31.03.2027");
        assertThat(fmt.country("AT")).isEqualTo("ÖSTERREICH");
        assertThat(fmt.country("GR")).isEqualTo("GRIECHENLAND");
        assertThat(fmt.iban("DE02120300000000202051")).isEqualTo("DE02 1203 0000 0000 2020 51");
        assertThat(fmt.iban("DE021203")).isEqualTo("DE02 1203");
        assertThat(fmt.percent(new BigDecimal("19"))).isEqualTo("19 %");
        assertThat(fmt.percent(new BigDecimal("7.00"))).isEqualTo("7 %");
    }

    @Test
    void writesTheUnitAfterTheQuantity() {
        assertThat(fmt.quantity(new BigDecimal("3.50"), "Std.")).isEqualTo("3,5\u00a0Std.");
        assertThat(fmt.quantity(new BigDecimal("24"), "UE")).isEqualTo("24\u00a0UE");
        assertThat(fmt.quantity(new BigDecimal("2"), "")).as("a piece, as before the units").isEqualTo("2");
    }

    @Test
    void signsATextOnTheLineAfterItsLast() {
        assertThat(fmt.signed("Mit freundlichen Grüßen", "Elena Sommer"))
                .isEqualTo("Mit freundlichen Grüßen\nElena Sommer");
        assertThat(fmt.signed(" Mit freundlichen Grüßen\n\n Elena Sommer \n", "Elena Sommer"))
                .as("a name of its own paragraph moves up").isEqualTo("Mit freundlichen Grüßen\nElena Sommer");
        assertThat(fmt.signed("Elena Sommer", "Elena Sommer")).isEqualTo("Elena Sommer");
        assertThat(fmt.signed("Hallo\nViele Grüße, Elena Sommer", "Elena Sommer"))
                .as("a last line that signs already stays whole").isEqualTo("Hallo\nViele Grüße, Elena Sommer");
        assertThat(fmt.signed("Grüße von Elena Sommer\n\nAnna", "Elena Sommer"))
                .as("only the last line counts").isEqualTo("Grüße von Elena Sommer\n\nAnna\nElena Sommer");
        assertThat(fmt.signed("Bitte antworten Sie ASAP", "SAP")).as("a name at the end of a word")
                .isEqualTo("Bitte antworten Sie ASAP\nSAP");
        assertThat(fmt.signed("Gruß,SAP", "SAP")).isEqualTo("Gruß,SAP");
        assertThat(fmt.signed("Gruß", "E")).isEqualTo("Gruß\nE");
    }
}
