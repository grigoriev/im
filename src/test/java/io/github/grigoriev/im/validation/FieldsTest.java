package io.github.grigoriev.im.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FieldsTest {

    @Test
    void isAUtilityClassWithAPrivateConstructor() throws Exception {
        Constructor<Fields> constructor = Fields.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }

    @Test
    void normalizesText() {
        assertThat(Fields.text(null)).isNull();
        assertThat(Fields.text("  ")).isNull();
        assertThat(Fields.text(" a b ")).isEqualTo("a b");
        assertThat(Fields.upper(" de ")).isEqualTo("DE");
        assertThat(Fields.upper(" ")).isNull();
        assertThat(Fields.compact(" de 12 3 ")).isEqualTo("DE123");
        assertThat(Fields.compact(null)).isNull();
    }

    @Test
    void requiresAndLimitsValues() {
        List<String> problems = new ArrayList<>();
        Fields.required(problems, null, "Name", 3);
        Fields.required(problems, "abcd", "Ort", 3);
        Fields.required(problems, "abc", "PLZ", 3);
        Fields.optional(problems, null, "Notiz", 3);
        Fields.optional(problems, "abcd", "Notiz", 3);
        assertThat(problems).containsExactly("Bitte Name angeben.", "Ort hat höchstens 3 Zeichen.",
                "Notiz hat höchstens 3 Zeichen.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "erika@example.org    | ",
        "erika.example.org    | Bitte eine gültige E-Mail-Adresse angeben.",
        "erika@example        | Bitte eine gültige E-Mail-Adresse angeben.",
        "'erika @example.org' | Bitte eine gültige E-Mail-Adresse angeben.",
        "                     | Bitte eine gültige E-Mail-Adresse angeben."
    })
    void checksAnEmailAddress(String email, String problem) {
        List<String> problems = new ArrayList<>();
        Fields.checkEmail(problems, email);
        assertThat(problems).isEqualTo(problem == null ? List.of() : List.of(problem));
    }

    @Test
    void limitsAnEmailAddressTo254Characters() {
        String local = "e".repeat(64);
        List<String> problems = new ArrayList<>();
        Fields.checkEmail(problems, local + "@" + "d".repeat(177) + ".example.org");
        assertThat(problems).isEmpty();
        Fields.checkEmail(problems, local + "@" + "d".repeat(178) + ".example.org");
        assertThat(problems).containsExactly("E-Mail hat höchstens 254 Zeichen.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "DE123456789     | true",
        "NL123456789B01  | true",
        "                | true",
        "123456789       | false",
        "DE1             | false",
        "DE1234567890123 | false"
    })
    void checksAnOptionalVatId(String vatId, boolean valid) {
        List<String> problems = new ArrayList<>();
        Fields.checkVatId(problems, vatId);
        assertThat(problems).hasSize(valid ? 0 : 1);
    }

    @ParameterizedTest
    @CsvSource({"DE, true", "CH, true", "D, false", "DEU, false", "de, false"})
    void checksACountryCode(String country, boolean valid) {
        List<String> problems = new ArrayList<>();
        Fields.checkCountry(problems, country);
        assertThat(problems).isEqualTo(valid ? List.of()
                : List.of("Das Land ist ein Code aus zwei Buchstaben, zum Beispiel DE."));
        List<String> missing = new ArrayList<>();
        Fields.checkCountry(missing, null);
        assertThat(missing).hasSize(1);
    }

    @Test
    void matchesAPattern() {
        List<String> problems = new ArrayList<>();
        Fields.matches(problems, "ab", Fields.COUNTRY, "falsch");
        Fields.matches(problems, "AB", Fields.COUNTRY, "falsch");
        assertThat(problems).containsExactly("falsch");
    }
}
