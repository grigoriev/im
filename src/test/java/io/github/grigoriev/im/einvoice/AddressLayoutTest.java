package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AddressLayoutTest {

    private static Party party(String name, String addition, String contact, String country) {
        return new Party(name, addition, "Seeweg 5", "86150", "Musterstadt", country, null, null, contact, null, null);
    }

    @Test
    void sixLinesKeepTheTextSize() {
        AddressLayout layout = AddressLayout.of(party("Sprachschule am See", "Abrechnungen Online-Sprachkurse",
                "Frau Weber", "AT"));
        assertThat(layout.lines()).isEqualTo(6);
        assertThat(layout.style()).isEqualTo("font-size: 9.5pt; line-height: 4.50mm");
    }

    @Test
    void aSecondLineOfTheNameMakesTheTextSmallerUntilEverythingFits() {
        String name = "x".repeat(42) + " y";
        AddressLayout layout = AddressLayout.of(party(name, "Abrechnungen Online-Sprachkurse", "Frau Weber", "AT"));
        assertThat(layout.style()).as("44 characters fit one line at 9 pt").isEqualTo("font-size: 9.0pt; line-height: 4.26mm");
        assertThat(layout.lines()).isEqualTo(6);
        assertThat(AddressLayout.of(party(name, null, "Frau Weber", "AT")).style()).as("two name lines and five more")
                .isEqualTo("font-size: 9.5pt; line-height: 4.50mm");
    }

    @Test
    void sevenLinesNeedASmallerSize() {
        String name = "Gemeinnützige Gesellschaft für Sprachförderung und interkulturelle Bildung Musterstadt mbH";
        AddressLayout layout = AddressLayout.of(party(name, "Abrechnungen Online-Sprachkurse", "Frau Weber", "AT"));
        assertThat(layout.lines() * layout.lineHeight()).isLessThanOrEqualTo(AddressLayout.FIELD_HEIGHT);
        assertThat(layout.style()).isEqualTo("font-size: 8.0pt; line-height: 3.79mm");
    }

    @Test
    void anAddressThatNeverFitsTakesTheSmallestSize() {
        String words = "Gemeinnützige Gesellschaft ".repeat(8).substring(0, 200);
        AddressLayout layout = AddressLayout.of(new Party(words, "a", words, "86150", words, "AT", null, null, "b",
                null, null));
        assertThat(layout.style()).isEqualTo("font-size: 6.0pt; line-height: 2.84mm");
    }

    @ParameterizedTest
    @CsvSource({
        "'Sprachschule am See', 42, 1",
        "'aaaa bbbb', 9, 1",
        "'aaaa bbbb', 8, 2",
        "'aaaa bbbb cccc', 9, 2",
        "'aaaaaaaaa', 4, 3",
        "'aaaaaaaa', 4, 2",
        "' x', 4, 1",
        "'aa aaaaaaaaa b', 4, 5"})
    void countsTheLinesOfAWrappedText(String text, int perLine, int lines) {
        assertThat(AddressLayout.wrapped(text, perLine)).isEqualTo(lines);
    }

    @ParameterizedTest
    @CsvSource({"9.5, 6", "9.0, 6", "8.0, 7", "6.0, 9"})
    void fitsThisManyLinesIntoTheHeightOfTheField(double size, int lines) {
        assertThat(AddressLayout.maxLines(size)).isEqualTo(lines);
    }

    @ParameterizedTest
    @CsvSource({"9.5, 42", "9.0, 44", "6.0, 66"})
    void fitsThisManyCharactersInTheWidthOfTheField(double size, int characters) {
        assertThat(AddressLayout.perLine(size)).isEqualTo(characters);
    }
}
