package io.github.grigoriev.im.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmountsTest {

    @Test
    void isAUtilityClassWithAPrivateConstructor() throws Exception {
        Constructor<Amounts> constructor = Amounts.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "45          | 45",
        "45,5        | 45.5",
        "45.50       | 45.50",
        "1.234,56    | 1234.56",
        "1.234.567,8 | 1234567.8",
        "' 400 € '   | 400",
        "-10,00      | -10.00",
        "0,0001      | 0.0001"
    })
    void readsAmountsAsGermansTypeThem(String text, BigDecimal amount) {
        assertThat(Amounts.parse(text)).isEqualByComparingTo(amount);
    }

    @ParameterizedTest
    @CsvSource({"1.500, true", "12.000, true", "-1.500, true", "' 1.500 € ', true", "1.5, false", "45.50, false",
        "'1.500,00', false", "1500, false", "1234.567, false"})
    void namesTheAmbiguousDotBeforeThreeDigits(String text, boolean ambiguous) {
        assertThat(Amounts.ambiguous(text)).isEqualTo(ambiguous);
        if (ambiguous) {
            assertThat(Amounts.parse(text)).isNull();
        }
        assertThat(Amounts.ambiguous(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"''", "abc", "1,2,3", "12.34.56", "1.23,4", "4 5x"})
    void refusesWhatIsNoNumber(String text) {
        assertThat(Amounts.parse(text)).isNull();
        assertThat(Amounts.parse(null)).isNull();
    }
}
