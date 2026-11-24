package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LineUnitTest {

    @ParameterizedTest
    @CsvSource({
        "PIECE, C62, Stück",
        "HOUR, HUR, Stunden",
        "TEACHING_UNIT, C62, UE (45 Min.)",
        "DAY, DAY, Tage"
    })
    void namesItsCodeAndItsChoice(LineUnit unit, String code, String choice) {
        assertThat(unit.code()).isEqualTo(code);
        assertThat(unit.choice()).isEqualTo(choice);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PIECE | 3 | ''",
        "HOUR | 1 | Std.",
        "HOUR | 3.5 | Std.",
        "TEACHING_UNIT | 24 | UE",
        "DAY | 1 | Tag",
        "DAY | 1.000 | Tag",
        "DAY | 0.5 | Tage",
        "DAY | 2 | Tage"
    })
    void printsItsLabelAfterTheQuantity(LineUnit unit, BigDecimal quantity, String label) {
        assertThat(unit.label(quantity)).isEqualTo(label);
    }
}
