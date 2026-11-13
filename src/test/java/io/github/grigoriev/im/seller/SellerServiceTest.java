package io.github.grigoriev.im.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;

class SellerServiceTest {

    private final StoredProfileRepository profiles = mock(StoredProfileRepository.class);
    private final JdbcAggregateOperations aggregates = mock(JdbcAggregateOperations.class);
    private final SellerService service = new SellerService(profiles, aggregates);

    private static SellerProfile with(String field, String value) {
        SellerProfile p = SellerTestData.profile();
        return new SellerProfile(
                field.equals("name") ? value : p.name(),
                field.equals("street") ? value : p.street(),
                field.equals("postcode") ? value : p.postcode(),
                field.equals("city") ? value : p.city(),
                field.equals("country") ? value : p.countryCode(),
                field.equals("contact") ? value : p.contactName(),
                field.equals("email") ? value : p.email(),
                field.equals("phone") ? value : p.phone(),
                field.equals("taxNumber") ? value : p.taxNumber(),
                field.equals("vatId") ? value : p.vatId(),
                field.equals("iban") ? value : p.iban(),
                field.equals("bic") ? value : p.bic(),
                p.taxStatus());
    }

    @Test
    void returnsTheStoredProfile() {
        when(profiles.findById(SellerService.ID)).thenReturn(Optional.of(new StoredProfile(1, SellerTestData.profile())));
        assertThat(service.find()).contains(SellerTestData.profile());
        when(profiles.findById(SellerService.ID)).thenReturn(Optional.empty());
        assertThat(service.find()).isEmpty();
    }

    @Test
    void insertsTheFirstProfileAndUpdatesItLater() {
        SellerProfile saved = service.save(SellerTestData.profile());
        assertThat(saved).isEqualTo(SellerTestData.profile());
        verify(aggregates).insert(new StoredProfile(1, saved));
        verify(aggregates, never()).update(any());

        when(profiles.existsById(SellerService.ID)).thenReturn(true);
        service.save(SellerTestData.profile());
        verify(aggregates).update(new StoredProfile(1, saved));
    }

    @Test
    void normalizesTheInput() {
        SellerProfile p = SellerTestData.profile();
        SellerProfile input = new SellerProfile("  Erika Mustermann ", p.street(), p.postcode(), p.city(), " de ", "  ",
                p.email(), p.phone(), "", " de 123 456 789 ", "de89 3704 0044 0532 0130 00", " cobadeffxxx ",
                TaxStatus.REGULAR);
        SellerProfile saved = service.save(input);
        assertThat(saved.name()).isEqualTo("Erika Mustermann");
        assertThat(saved.countryCode()).isEqualTo("DE");
        assertThat(saved.contactName()).isNull();
        assertThat(saved.taxNumber()).isNull();
        assertThat(saved.vatId()).isEqualTo("DE123456789");
        assertThat(saved.iban()).isEqualTo(SellerTestData.IBAN);
        assertThat(saved.bic()).isEqualTo("COBADEFFXXX");
        assertThat(saved.taxStatus()).isEqualTo(TaxStatus.REGULAR);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "name      | ''                                | Bitte Name angeben.",
        "street    |                                   | Bitte Straße angeben.",
        "postcode  | ' '                               | Bitte PLZ angeben.",
        "city      | ''                                | Bitte Ort angeben.",
        "country   | DEU                               | Das Land ist ein Code aus zwei Buchstaben, zum Beispiel DE.",
        "country   | ''                                | Das Land ist ein Code aus zwei Buchstaben, zum Beispiel DE.",
        "email     | erika.example.org                 | Bitte eine gültige E-Mail-Adresse angeben.",
        "phone     | Telefon                           | Bitte eine Telefonnummer angeben, zum Beispiel +49 89 1234567.",
        "vatId     | 123456789                         | Die USt-IdNr. beginnt mit dem Ländercode, zum Beispiel DE123456789.",
        "iban      | DE89370400440532013001            | Die IBAN ist ungültig.",
        "iban      | DE8937040044                      | Die IBAN ist ungültig.",
        "iban      | ''                                | Die IBAN ist ungültig.",
        "bic       | COBADEF                           | Die BIC hat 8 oder 11 Zeichen."
    })
    void refusesAWrongField(String field, String value, String problem) {
        SellerProfile wrong = with(field, value);
        SettingsException e = catchThrowableOfType(SettingsException.class, () -> service.save(wrong));
        assertThat(e.problems()).containsExactly(problem);
        verify(aggregates, never()).insert(any());
    }

    @ParameterizedTest
    @CsvSource({"name, 200, Name", "street, 200, Straße", "postcode, 16, PLZ", "city, 100, Ort",
        "contact, 200, Ansprechpartner", "taxNumber, 32, Steuernummer"})
    void limitsTheLength(String field, int max, String label) {
        SellerProfile longest = with(field, "x".repeat(max));
        assertThat(SellerService.check(SellerService.normalize(longest))).isEmpty();
        assertThat(SellerService.check(SellerService.normalize(with(field, "x".repeat(max + 1)))))
                .containsExactly(label + " hat höchstens " + max + " Zeichen.");
    }

    @Test
    void needsATaxNumberOrAVatIdAndATaxStatus() {
        SellerProfile p = SellerTestData.profile();
        SellerProfile none = new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), p.countryCode(), null,
                p.email(), p.phone(), null, null, p.iban(), p.bic(), null);
        assertThat(SellerService.check(none)).containsExactly("Bitte die Steuernummer oder die USt-IdNr. angeben.",
                "Bitte den Steuerstatus wählen.");
        assertThat(SellerService.check(with("vatId", "DE123456789"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"DE89370400440532013000, true", "GB82WEST12345698765432, true", "AT611904300234573201, true",
        "DE89370400440532013001, false", "GB82WEST12345698765433, false"})
    void checksTheIbanChecksum(String iban, boolean valid) {
        assertThat(SellerService.checksumOk(iban)).isEqualTo(valid);
    }

    @Test
    void acceptsAnEightCharacterBicAndAPhoneWithoutPlus() {
        assertThat(SellerService.check(with("bic", "COBADEFF"))).isEmpty();
        assertThat(SellerService.check(with("phone", "030 1234567"))).isEmpty();
    }
}
