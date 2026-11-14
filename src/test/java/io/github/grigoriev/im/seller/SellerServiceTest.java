package io.github.grigoriev.im.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class SellerServiceTest {

    private final StoredProfileRepository profiles = mock(StoredProfileRepository.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec statement = mock(StatementSpec.class);
    private final AuditService audit = mock(AuditService.class);
    private final SellerService service = new SellerService(profiles, jdbc, audit);

    @org.junit.jupiter.api.BeforeEach
    void statement() {
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
    }

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
                p.taxStatus(),
                field.equals("tagline") ? value : p.tagline(),
                field.equals("website") ? value : p.website(),
                field.equals("bankName") ? value : p.bankName(),
                field.equals("holder") ? value : p.accountHolder());
    }

    /** Saves a whole profile: the stored one equals the input, so every part of the merge is the input. */
    private SellerProfile saveWhole(SellerProfile input) {
        when(profiles.findById(SellerService.ID)).thenReturn(Optional.of(new StoredProfile(1, input)));
        return service.save(ProfilePart.ADDRESS, input);
    }

    @Test
    void savesOnePartAndKeepsTheOthersAsStored() {
        when(profiles.findById(SellerService.ID)).thenReturn(Optional.of(new StoredProfile(1, SellerTestData.profile())));
        SellerProfile bank = new SellerProfile(null, null, null, null, null, null, null, null, null, null,
                "GB82 WEST 1234 5698 7654 32", "WESTGB2L", null, null, null, "Westbank", "Erika M.");
        SellerProfile saved = service.save(ProfilePart.BANK, bank);
        assertThat(saved.name()).isEqualTo("Erika Mustermann");
        assertThat(saved.taxNumber()).isEqualTo("12/345/67890");
        assertThat(saved.iban()).isEqualTo("GB82WEST12345698765432");
        assertThat(saved.accountHolder()).isEqualTo("Erika M.");

        when(profiles.findById(SellerService.ID)).thenReturn(Optional.empty());
        SellerProfile first = service.save(ProfilePart.CONTACT, SellerTestData.profile());
        assertThat(first.email()).isEqualTo("erika@example.org");
        assertThat(first.name()).as("nothing stored before the first save").isNull();
        assertThat(first.countryCode()).as("the defaults of the first form").isEqualTo("DE");
        assertThat(first.taxStatus()).isEqualTo(TaxStatus.KLEINUNTERNEHMER);
    }

    @Test
    void returnsTheStoredProfile() {
        when(profiles.findById(SellerService.ID)).thenReturn(Optional.of(new StoredProfile(1, SellerTestData.profile())));
        assertThat(service.find()).contains(SellerTestData.profile());
        when(profiles.findById(SellerService.ID)).thenReturn(Optional.empty());
        assertThat(service.find()).isEmpty();
    }

    @Test
    void writesTheProfileInOneUpsert() {
        SellerProfile saved = saveWhole(SellerTestData.profile());
        assertThat(saved).isEqualTo(SellerTestData.profile());
        verify(jdbc).sql(SellerService.UPSERT);
        verify(statement).param("name", "Erika Mustermann");
        verify(statement).param("street", "Musterweg 1");
        verify(statement).param("postcode", "12345");
        verify(statement).param("city", "Musterstadt");
        verify(statement).param("countryCode", "DE");
        verify(statement).param("contactName", null);
        verify(statement).param("email", "erika@example.org");
        verify(statement).param("phone", "+49 30 1234567");
        verify(statement).param("taxNumber", "12/345/67890");
        verify(statement).param("vatId", null);
        verify(statement).param("iban", SellerTestData.IBAN);
        verify(statement).param("bic", "COBADEFFXXX");
        verify(statement).param("taxStatus", "KLEINUNTERNEHMER");
        verify(statement).param("tagline", "Freiberufliche Dozentin");
        verify(statement).param("website", "www.erika.example");
        verify(statement).param("bankName", "Commerzbank");
        verify(statement).param("accountHolder", null);
        verify(statement).update();
        assertThat(SellerService.UPSERT).contains("ON CONFLICT (id) DO UPDATE");
        verify(audit).add(AuditAction.PROFILE_SAVED, "Erika Mustermann");
    }

    @Test
    void normalizesTheInput() {
        SellerProfile p = SellerTestData.profile();
        SellerProfile input = new SellerProfile("  Erika Mustermann ", p.street(), p.postcode(), p.city(), " de ", "  ",
                p.email(), p.phone(), "", " de 123 456 789 ", "de89 3704 0044 0532 0130 00", " cobadeffxxx ",
                TaxStatus.REGULAR, null, null, null, null);
        SellerProfile saved = saveWhole(input);
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
        "country   | DEU                               | countryCode | Bitte ein Land aus der Liste wählen.",
        "email     | erika.example.org                 | email       | Bitte eine gültige E-Mail-Adresse angeben.",
        "phone     | Telefon                           | phone       | Bitte eine Telefonnummer angeben, zum Beispiel +49 89 1234567.",
        "vatId     | 123456789                         | vatId       | Die USt-IdNr. beginnt mit dem Ländercode, zum Beispiel DE123456789.",
        "iban      | DE89370400440532013001            | iban        | Die IBAN ist ungültig.",
        "iban      | DE8937040044                      | iban        | Die IBAN ist ungültig.",
        "iban      | DE813704004405320130000           | iban        | Eine IBAN aus DE hat 22 Zeichen.",
        "phone     | +12345678901234567890123456789012345678901234567890 | phone | Bitte eine Telefonnummer angeben, zum Beispiel +49 89 1234567.",
        "bic       | COBADEF                           | bic         | Die BIC hat 8 oder 11 Zeichen."
    })
    void refusesAWrongField(String field, String value, String key, String problem) {
        SellerProfile wrong = with(field, value);
        SettingsException e = catchThrowableOfType(SettingsException.class, () -> saveWhole(wrong));
        assertThat(e.fields()).containsExactly(java.util.Map.entry(key, problem));
        assertThat(e.problems()).containsExactly(problem);
        verify(statement, never()).update();
        org.mockito.Mockito.verifyNoInteractions(audit);
    }

    @Test
    void savesAnEmptyProfileAndNamesWhatInvoicesStillNeed() {
        SellerProfile empty = new SellerProfile(" ", null, "", null, "", null, "", null, null, null, "", null, null,
                null, null, null, null);
        SellerProfile saved = saveWhole(empty);
        verify(statement).param("taxStatus", null);
        verify(statement).update();
        verify(audit).add(io.github.grigoriev.im.audit.AuditAction.PROFILE_SAVED, "");
        assertThat(SellerService.missing(saved)).containsExactly(java.util.Map.entry("name", "Name"),
                java.util.Map.entry("street", "Straße"), java.util.Map.entry("postcode", "PLZ"),
                java.util.Map.entry("city", "Ort"), java.util.Map.entry("countryCode", "Land"),
                java.util.Map.entry("taxStatus", "Steuerstatus"),
                java.util.Map.entry("taxNumber", "Steuernummer oder USt-IdNr."),
                java.util.Map.entry("iban", "IBAN"), java.util.Map.entry("bic", "BIC"));
        assertThat(SellerService.missingForXRechnung(empty)).containsExactly(java.util.Map.entry("email", "E-Mail"),
                java.util.Map.entry("phone", "Telefon"));
        assertThat(SellerService.missing(SellerProfile.empty())).as("the first form, normalized")
                .containsKeys("name", "iban").doesNotContainKeys("countryCode", "taxStatus");
    }

    @Test
    void aCompleteProfileMissesNothingAndATaxNumberOrAVatIdIsEnough() {
        assertThat(SellerService.missing(SellerTestData.profile())).isEmpty();
        assertThat(SellerService.missingForXRechnung(SellerTestData.profile())).isEmpty();
        SellerProfile neither = with("taxNumber", null);
        assertThat(SellerService.missing(neither)).containsOnlyKeys("taxNumber");
        assertThat(SellerService.missing(new SellerProfile(neither.name(), neither.street(), neither.postcode(),
                neither.city(), neither.countryCode(), null, null, null, null, "DE123456789", neither.iban(),
                neither.bic(), neither.taxStatus(), null, null, null, null))).as("a VAT ID is enough").isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"name, 200, Name, name", "street, 200, Straße, street", "postcode, 16, PLZ, postcode",
        "city, 100, Ort, city", "contact, 200, Ansprechpartner, contactName", "taxNumber, 32, Steuernummer, taxNumber",
        "tagline, 200, Zusatz, tagline", "bankName, 100, Kreditinstitut, bankName",
        "holder, 200, Kontoinhaber, accountHolder"})
    void limitsTheLength(String field, int max, String label, String key) {
        SellerProfile longest = with(field, "x".repeat(max));
        assertThat(SellerService.check(SellerService.normalize(longest))).isEmpty();
        assertThat(SellerService.check(SellerService.normalize(with(field, "x".repeat(max + 1)))))
                .containsExactly(java.util.Map.entry(key, label + " hat höchstens " + max + " Zeichen."));
    }

    @ParameterizedTest
    @CsvSource({"DE89370400440532013000, true", "GB82WEST12345698765432, true", "AT611904300234573201, true",
        "DE89370400440532013001, false", "GB82WEST12345698765433, false"})
    void checksTheIbanChecksum(String iban, boolean valid) {
        assertThat(SellerService.checksumOk(iban)).isEqualTo(valid);
    }

    @Test
    void limitsPhoneAndEmailToTheirColumns() {
        assertThat(SellerService.check(with("phone", "+" + "1".repeat(49)))).isEmpty();
        String local = "e".repeat(64);
        String domain = "d".repeat(177) + ".example.org"; // 64 + 1 + 189 = 254 characters
        assertThat(SellerService.check(with("email", local + "@" + domain))).isEmpty();
        assertThat(SellerService.check(with("email", local + "@d" + domain)))
                .containsExactly(java.util.Map.entry("email", "E-Mail hat höchstens 254 Zeichen."));
    }

    @Test
    void acceptsAnIbanOfAnyLengthFromACountryOutsideSepa() {
        // Saint Lucia (LC), 32 characters: valid checksum, not in the SEPA table.
        assertThat(SellerService.check(with("iban", "LC55HEMM000100010012001200023015"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"www.erika.example, true", "https://erika.example/kurse, true", "http://a-b.example.de, true",
        "erika, false", "https://, false", "www.erika example.de, false", "mailto:erika@example.org, false",
        ".example.de, false", "example.de., false", "www..example.de, false", "https://example.de/a b, false",
        "www.müller-sprachkurse.de, true", "HTTPS://Example.DE, true", "www.straße.de/kurse, true",
        "www.exa_mple.de, false", "ftp://example.de, false"})
    void checksTheWebsite(String website, boolean valid) {
        assertThat(SellerService.check(with("website", website))).isEqualTo(valid ? java.util.Map.of()
                : java.util.Map.of("website", "Bitte eine Website wie www.example.de angeben."));
    }

    @Test
    void limitsTheWebsiteTo200Characters() {
        // 200 characters in labels of at most 63, as DNS allows: 4 + 3 * 61 - 1 + 14.
        String label = "a".repeat(60);
        String host = "www." + label + "." + label + "." + label + ".bbbbb.example";
        assertThat(SellerService.check(with("website", host))).isEmpty();
        assertThat(SellerService.check(with("website", host + "x"))).containsExactly(java.util.Map.entry("website", "Website hat höchstens 200 Zeichen."));
    }

    @Test
    void stripsTheOptionalLinesAndDropsEmptyOnes() {
        SellerProfile p = SellerTestData.profile();
        SellerProfile normalized = SellerService.normalize(new SellerProfile(p.name(), p.street(), p.postcode(), p.city(),
                p.countryCode(), null, p.email(), p.phone(), p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(),
                "  Dozentin ", null, " Commerzbank ", "  "));
        assertThat(normalized.tagline()).isEqualTo("Dozentin");
        assertThat(normalized.accountHolder()).isNull();
        assertThat(normalized.bankName()).isEqualTo("Commerzbank");
        assertThat(SellerService.normalize(with("website", " www.erika.example ")).website()).isEqualTo("www.erika.example");
    }

    @Test
    void acceptsAnEightCharacterBicAndAPhoneWithoutPlus() {
        assertThat(SellerService.check(with("bic", "COBADEFF"))).isEmpty();
        assertThat(SellerService.check(with("phone", "030 1234567"))).isEmpty();
    }
}
