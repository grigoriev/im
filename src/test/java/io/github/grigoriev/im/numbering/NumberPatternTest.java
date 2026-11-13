package io.github.grigoriev.im.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.grigoriev.im.seller.SettingsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NumberPatternTest {

    @ParameterizedTest
    @CsvSource({
        "'{YYYY}-{NN}', 2027, 1, 2027-01",
        "'{YYYY}-{NN}', 2027, 123, 2027-123",
        "'RE-{YY}/{NNN}', 2027, 7, RE-27/007",
        "'{N}.{YYYY}', 2030, 12, 12.2030",
        "'{YY}{NNNNNN}', 2105, 42, 05000042",
        "'{YYYY}_{N}', 999, 3, 0999_3"
    })
    void formatsTheNumber(String pattern, int year, int counter, String number) {
        assertThat(NumberPattern.parse(pattern).format(year, counter)).isEqualTo(number);
    }

    @Test
    void defaultsToYearAndTwoDigits() {
        assertThat(NumberPattern.DEFAULT.format(2027, 1)).isEqualTo("2027-01");
        assertThat(NumberPattern.parse("  {YYYY}-{NN} ")).isEqualTo(NumberPattern.DEFAULT);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{NN}                | Das Muster braucht das Jahr, {YYYY} oder {YY}: der Zähler beginnt jedes Jahr neu.",
        "{YYYY}              | Das Muster braucht genau einen Zähler, zum Beispiel {NN}.",
        "{YYYY}-{N}-{NN}     | Das Muster braucht genau einen Zähler, zum Beispiel {NN}.",
        "{YYYY}-{NN}-{NNNNNNN} | Unbekannter Platzhalter {NNNNNNN}.",
        "{YYYY}-{NN}-{MM}    | Unbekannter Platzhalter {MM}.",
        "{YYYY} {NN}         | Fester Text besteht aus Buchstaben, Ziffern und - _ . /",
        "{YYYY}-{NN}}        | Fester Text besteht aus Buchstaben, Ziffern und - _ . /",
        "{YYYY}-{NN}{        | Fester Text besteht aus Buchstaben, Ziffern und - _ . /",
        "RECHNUNGS-NUMMER-{YYYY}-{NNNNNN}X | Das Muster hat höchstens 32 Zeichen."
    })
    void refusesAPatternThatCannotWork(String pattern, String problem) {
        SettingsException e = catchThrowableOfType(SettingsException.class, () -> NumberPattern.parse(pattern));
        assertThat(e.problems()).containsExactly(problem);
    }

    @Test
    void reportsEveryProblemOfAnEmptyPattern() {
        SettingsException e = catchThrowableOfType(SettingsException.class, () -> NumberPattern.parse(null));
        assertThat(e.problems()).hasSize(2);
        assertThat(NumberPattern.parse("RECHNUNGS-NUMMER-{YYYY}-{NNNNNN}").value()).hasSize(32);
    }
}
