package io.github.grigoriev.im.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.setting.SettingKey;
import io.github.grigoriev.im.setting.SettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

class ProcedureServiceTest {

    private static final Instant NOW = Instant.parse("2027-01-04T09:00:00Z");
    private static final byte[] PDF = "%PDF-1.7".getBytes();

    private final SettingRepository settingRepository = mock(SettingRepository.class);
    private final SellerService sellers = mock(SellerService.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final InvoicePdfRenderer pdf = mock(InvoicePdfRenderer.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<BuildProperties> build = mock(ObjectProvider.class);
    private final ProcedureService service = new ProcedureService(settingRepository, sellers, documents, pdf, build,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Map<String, String> settings = new HashMap<>();

    @BeforeEach
    void stubs() {
        // The stored settings by their key in the database.
        when(settingRepository.read(any())).thenAnswer(
                call -> Optional.ofNullable(settings.get(call.<SettingKey>getArgument(0).stored())));
        doAnswer(call -> settings.put(call.<SettingKey>getArgument(0).stored(), call.getArgument(1)))
                .when(settingRepository).write(any(), any());
        when(sellers.find()).thenReturn(Optional.of(SellerTestData.profile()));
        when(pdf.render(eq("pdf/procedure"), any())).thenReturn(PDF);
        when(documents.list()).thenReturn(List.of());
    }

    private static ProcedureForm form(String change) {
        ProcedureForm form = new ProcedureForm();
        form.setValidFrom(LocalDate.of(2027, 1, 1));
        form.setExpenses(" WISO Steuern ");
        form.setArchive("in einem Dropbox-Ordner");
        form.setChange(change);
        return form;
    }

    private static BusinessDocument version(long id, Long replacedBy, String filename, LocalDate date, String note,
            Instant createdAt) {
        return new BusinessDocument(id, DocumentCategory.PROCEDURE, ProcedureService.TITLE, date, null, note, null,
                replacedBy, filename, "application/pdf", "h", 1, createdAt, "anna");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rendered() {
        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(pdf).render(eq("pdf/procedure"), values.capture());
        return values.getValue();
    }

    @Test
    void startsAFirstVersionAndKeepsTheValuesForTheNext() {
        ProcedureForm empty = service.form();
        assertThat(empty.getValidFrom()).isNull();
        assertThat(empty.getExpenses()).isNull();
        assertThat(empty.getArchive()).isNull();
        assertThat(empty.getChange()).isEqualTo("Erstfassung");
        BusinessDocument added = version(1, null, "Verfahrensdokumentation_2027-01-04.pdf", LocalDate.of(2027, 1, 4),
                "Erstfassung", NOW);
        when(documents.add(any(), anyString(), any())).thenReturn(added);
        assertThat(service.save(form(" Erstfassung "))).isEqualTo(added);
        ArgumentCaptor<DocumentForm> description = ArgumentCaptor.forClass(DocumentForm.class);
        verify(documents).add(description.capture(), eq("Verfahrensdokumentation_2027-01-04.pdf"), eq(PDF));
        assertThat(description.getValue().getCategory()).isEqualTo(DocumentCategory.PROCEDURE);
        assertThat(description.getValue().getTitle()).isEqualTo("Verfahrensdokumentation");
        assertThat(description.getValue().getDocumentDate()).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(description.getValue().getNote()).isEqualTo("Erstfassung");
        assertThat(settings).containsEntry("procedure-valid-from", "2027-01-01")
                .containsEntry("procedure-expenses", "WISO Steuern")
                .containsEntry("procedure-archive", "in einem Dropbox-Ordner");

        when(documents.list()).thenReturn(List.of(added));
        ProcedureForm next = service.form();
        assertThat(next.getValidFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(next.getExpenses()).isEqualTo("WISO Steuern");
        assertThat(next.getArchive()).isEqualTo("in einem Dropbox-Ordner");
        assertThat(next.getChange()).as("a later version names its own change").isNull();
    }

    @Test
    void aLaterVersionReplacesTheCurrentOneAndListsTheChangesOfTheAppsVersions() {
        BusinessDocument first = version(1, 2L, "Verfahrensdokumentation_2027-01-04.pdf", LocalDate.of(2027, 1, 4),
                "Erstfassung", NOW);
        BusinessDocument scan = version(2, 3L, "Scan.pdf", LocalDate.of(2027, 1, 5), "unterschrieben",
                NOW.plusSeconds(86_400));
        BusinessDocument second = version(3, null, "Verfahrensdokumentation_2027-06-01.pdf", LocalDate.of(2027, 6, 1),
                "Version 0.2.0", NOW.plusSeconds(200 * 86_400));
        BusinessDocument other = new BusinessDocument(9, DocumentCategory.TAX_ASSESSMENT, "Bescheid", LocalDate.of(2027,
                5, 1), null, "", null, null, "b.pdf", "application/pdf", "h", 1, NOW.plusSeconds(999_999_999), "anna");
        when(documents.list()).thenReturn(List.of(other, second));
        when(documents.versions(3)).thenReturn(List.of(second, scan, first));
        when(documents.replace(eq(3L), any(), anyString(), any())).thenReturn(version(4, null,
                "Verfahrensdokumentation_2027-01-04.pdf", LocalDate.of(2027, 1, 4), "Neue Steuernummer", NOW));
        assertThat(service.current()).contains(second);
        service.save(form("Neue Steuernummer"));
        verify(documents).replace(eq(3L), any(), eq("Verfahrensdokumentation_2027-01-04.pdf"), eq(PDF));
        verify(documents, never()).add(any(), anyString(), any());
        assertThat(rendered().get("changes")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly(new ProcedureService.Change("04.01.2027", "Erstfassung"),
                        new ProcedureService.Change("01.06.2027", "Version 0.2.0"),
                        new ProcedureService.Change("04.01.2027", "Neue Steuernummer"));
    }

    @Test
    void thePreviewRendersTheValuesAndSavesNothing() {
        Properties values = new Properties();
        values.setProperty("version", "0.1.4");
        when(build.getIfAvailable()).thenReturn(new BuildProperties(values));
        assertThat(service.preview(form("Erstfassung"))).isEqualTo(PDF);
        Map<String, Object> rendered = rendered();
        assertThat(rendered).containsEntry("seller", SellerTestData.profile()).containsEntry("validFrom", "01.01.2027")
                .containsEntry("expenses", "WISO Steuern").containsEntry("archive", "in einem Dropbox-Ordner")
                .containsEntry("version", "0.1.4");
        assertThat(settings).isEmpty();
        verify(documents, never()).add(any(), anyString(), any());
        verify(documents, never()).replace(anyLong(), any(), anyString(), any());
    }

    @Test
    void namesTheMissingVersion() {
        assertThat(service.version()).isEqualTo("ohne Versionsangabe");
    }

    @Test
    void namesEveryMissingValue() {
        ProcedureForm empty = new ProcedureForm();
        empty.setChange(" ");
        when(sellers.find()).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.save(empty)).problems()).containsExactly(
                "Bitte das Datum angeben, ab dem das Verfahren gilt.",
                "Bitte das Programm für Belege und Ausgaben angeben.", "Bitte den Ablageort der Jahresarchive angeben.",
                "Bitte die Änderung angeben.", ProcedureService.NO_PROFILE);
        ProcedureForm long_ = form("x".repeat(201));
        when(sellers.find()).thenReturn(Optional.of(SellerTestData.profile()));
        assertThat(catchThrowableOfType(DocumentException.class, () -> service.preview(long_)).problems())
                .containsExactly("die Änderung hat höchstens 200 Zeichen.");
        verify(pdf, never()).render(anyString(), any());
    }

    @Test
    void needsANameAnAddressAndATaxNumber() {
        SellerProfile p = SellerTestData.profile();
        List<SellerProfile> incomplete = List.of(
                new SellerProfile(" ", p.street(), p.postcode(), p.city(), p.countryCode(), null, p.email(), p.phone(),
                        p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null, null, null, null, null, false),
                new SellerProfile(p.name(), null, p.postcode(), p.city(), p.countryCode(), null, p.email(), p.phone(),
                        p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null, null, null, null, null, false),
                new SellerProfile(p.name(), p.street(), null, p.city(), p.countryCode(), null, p.email(), p.phone(),
                        p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null, null, null, null, null, false),
                new SellerProfile(p.name(), p.street(), p.postcode(), "", p.countryCode(), null, p.email(), p.phone(),
                        p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null, null, null, null, null, false),
                new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), p.countryCode(), null, p.email(),
                        p.phone(), null, "DE123456789", p.iban(), p.bic(), p.taxStatus(), null, null, null, null, null, false));
        for (SellerProfile seller : incomplete) {
            when(sellers.find()).thenReturn(Optional.of(seller));
            assertThat(catchThrowableOfType(DocumentException.class, () -> service.preview(form("Erstfassung")))
                    .problems()).containsExactly(ProcedureService.NO_PROFILE);
        }
    }

    @Test
    void theFormKeepsItsValues() {
        ProcedureForm form = form("Erstfassung");
        assertThat(form.getValidFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(form.getExpenses()).isEqualTo(" WISO Steuern ");
        assertThat(form.getArchive()).isEqualTo("in einem Dropbox-Ordner");
        assertThat(form.getChange()).isEqualTo("Erstfassung");
    }
}
