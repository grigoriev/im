package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.seller.TaxNoteService;
import io.github.grigoriev.im.seller.TaxStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InvoiceServiceTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 18);

    private final InvoiceRepository repository = mock(InvoiceRepository.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final SellerService sellers = mock(SellerService.class);
    private final TaxNoteService notes = mock(TaxNoteService.class);
    private final AuditService audit = mock(AuditService.class);
    private final InvoiceService service = new InvoiceService(repository, customers, sellers, notes, audit,
            Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));

    @BeforeEach
    void defaults() {
        when(sellers.find()).thenReturn(Optional.of(SellerTestData.profile()));
        when(customers.find(2)).thenReturn(Optional.of(CustomerTestData.verlag().withId(2L)));
        when(notes.note(TaxCategory.KLEINUNTERNEHMER)).thenReturn("Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.");
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static Invoice draft(List<DraftLine> lines) {
        return new Invoice(5L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.ZUGFERD_EN16931, TODAY, TODAY, TODAY, 14,
                null, "Hallo", "Danke", "Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.", lines, NOW, NOW, 0L);
    }

    private static DraftLine lump(String text, String amount, TaxCategory category) {
        return new DraftLine(text, true, BigDecimal.ONE, new BigDecimal(amount), category);
    }

    private static SellerProfile seller(TaxStatus status, String vatId) {
        SellerProfile p = SellerTestData.profile();
        return new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), p.countryCode(), null, p.email(), p.phone(),
                p.taxNumber(), vatId, p.iban(), p.bic(), status, null, null, null, null);
    }

    private static DraftForm form(LineForm... lines) {
        DraftForm form = DraftForm.of(draft(List.of()));
        form.getLines().addAll(List.of(lines));
        return form;
    }

    private static LineForm row(String text, boolean lumpSum, String quantity, String price, TaxCategory category) {
        LineForm row = new LineForm();
        row.setText(text);
        row.setLumpSum(lumpSum);
        row.setQuantity(quantity);
        row.setPrice(price);
        row.setCategory(category);
        return row;
    }

    @Test
    void listsAndFindsInvoices() {
        when(repository.findAllByOrderByIdDesc()).thenReturn(List.of(draft(List.of())));
        assertThat(service.list()).hasSize(1);
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        assertThat(service.find(5)).isPresent();
    }

    @Test
    void createsADraftFromTheCustomerAndTheTaxStatus() {
        Invoice created = service.createDraft(2);
        assertThat(created.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(created.customerId()).isEqualTo(2L);
        assertThat(created.number()).isNull();
        assertThat(created.format()).isEqualTo(InvoiceFormat.ZUGFERD_EN16931);
        assertThat(created.issueDate()).isEqualTo(TODAY);
        assertThat(created.serviceFrom()).isEqualTo(TODAY);
        assertThat(created.serviceTo()).isEqualTo(TODAY);
        assertThat(created.paymentDays()).isEqualTo(14);
        assertThat(created.intro()).isEqualTo(InvoiceService.DEFAULT_INTRO);
        assertThat(created.closing()).isEqualTo(InvoiceService.DEFAULT_CLOSING);
        assertThat(created.taxNote()).isEqualTo("Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.");
        assertThat(created.lines()).containsExactly(DraftLine.empty(TaxCategory.KLEINUNTERNEHMER));
        assertThat(created.createdAt()).isEqualTo(NOW);
        verify(audit).add(AuditAction.DRAFT_CREATED, "Verlag am Fluss GmbH");
    }

    @Test
    void takesFormatAndBuyerReferenceFromTheCustomer() {
        when(customers.find(3)).thenReturn(Optional.of(CustomerTestData.vhs().withId(3L)));
        Invoice created = service.createDraft(3);
        assertThat(created.format()).isEqualTo(InvoiceFormat.XRECHNUNG);
        assertThat(created.buyerReference()).isEqualTo("09162000-VHS01-42");
    }

    @Test
    void chargesVatByDefaultWithRegularTaxation() {
        when(sellers.find()).thenReturn(Optional.of(seller(TaxStatus.REGULAR, "DE123456789")));
        assertThat(service.defaultCategory()).isEqualTo(TaxCategory.STANDARD_19);
        when(sellers.find()).thenReturn(Optional.empty());
        assertThat(service.defaultCategory()).isEqualTo(TaxCategory.KLEINUNTERNEHMER);
    }

    @Test
    void needsTheProfileAndTheCustomer() {
        when(customers.find(9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createDraft(9)).isInstanceOf(InvoiceException.class)
                .hasMessage("Diesen Kunden gibt es nicht.");
        when(sellers.find()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createDraft(2)).isInstanceOf(InvoiceException.class)
                .hasMessage("Bitte zuerst das Profil in den Einstellungen ausfüllen.");
        verify(repository, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void savesTheEnteredDraft() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        DraftForm form = form(row("  Unterricht ", false, "16", "45,00", TaxCategory.EXEMPT_4_21),
                row("Lesung", true, "1", "1.234,50", TaxCategory.EXEMPT_4_21));
        form.setIntro(" ");
        form.setBuyerReference(" 04011000-12345-67 ");
        Invoice saved = service.saveDraft(5, form);
        assertThat(saved.lines()).containsExactly(
                new DraftLine("Unterricht", false, new BigDecimal("16"), new BigDecimal("45.00"), TaxCategory.EXEMPT_4_21),
                new DraftLine("Lesung", true, BigDecimal.ONE, new BigDecimal("1234.50"), TaxCategory.EXEMPT_4_21));
        assertThat(saved.intro()).isNull();
        assertThat(saved.buyerReference()).isEqualTo("04011000-12345-67");
        assertThat(saved.closing()).isEqualTo("Danke");
        assertThat(saved.number()).isNull();
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesUnreadableNumbersAndTooLongTexts() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        DraftForm form = form(row("x".repeat(501), false, "abc", "1,23456", null));
        form.setBuyerReference("r".repeat(201));
        form.setIntro("i".repeat(2001));
        form.setClosing("c".repeat(2001));
        form.setTaxNote("t".repeat(1001));
        form.setPaymentDays(366);
        InvoiceException e = catchThrowableOfType(InvoiceException.class, () -> service.saveDraft(5, form));
        assertThat(e.problems()).containsExactly(
                "Position 1: Die Leistung hat höchstens 500 Zeichen.",
                "Position 1: Bitte die Steuer wählen.",
                "Position 1: Der Preis hat höchstens 4 Nachkommastellen.",
                "Position 1: Die Menge ist keine Zahl, zum Beispiel 45,00.",
                "Die Käuferreferenz hat höchstens 200 Zeichen.",
                "Die Einleitung hat höchstens 2000 Zeichen.",
                "Der Schlusstext hat höchstens 2000 Zeichen.",
                "Der Steuerhinweis hat höchstens 1000 Zeichen.",
                "Das Zahlungsziel liegt zwischen 0 und 365 Tagen.");
        verify(repository, never()).save(any());
    }

    @Test
    void acceptsTheLimits() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        DraftForm form = form(row("x".repeat(500), false, "1,5", "0,0001", TaxCategory.KLEINUNTERNEHMER));
        form.setBuyerReference("r".repeat(200));
        form.setIntro("i".repeat(2000));
        form.setClosing("c".repeat(2000));
        form.setTaxNote("t".repeat(1000));
        form.setPaymentDays(365);
        assertThat(service.saveDraft(5, form).paymentDays()).isEqualTo(365);
        form.setPaymentDays(0);
        assertThat(service.saveDraft(5, form).paymentDays()).isZero();
        form.setPaymentDays(-1);
        assertThatThrownBy(() -> service.saveDraft(5, form)).isInstanceOf(InvoiceException.class);
    }

    @Test
    void needsFormatAndDates() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        for (int field = 0; field < 4; field++) {
            DraftForm form = form();
            switch (field) {
                case 0 -> form.setFormat(null);
                case 1 -> form.setIssueDate(null);
                case 2 -> form.setServiceFrom(null);
                default -> form.setServiceTo(null);
            }
            InvoiceException e = catchThrowableOfType(InvoiceException.class, () -> service.saveDraft(5, form));
            assertThat(e.problems()).containsExactly("Bitte Format, Rechnungsdatum und Leistungszeitraum angeben.");
        }
    }

    @Test
    void changesOnlyAnExistingDraft() {
        when(repository.findById(6L)).thenReturn(Optional.empty());
        DraftForm form = form();
        assertThatThrownBy(() -> service.saveDraft(6, form)).isInstanceOf(InvoiceException.class)
                .hasMessage("Diese Rechnung gibt es nicht.");
        Invoice issued = new Invoice(7L, InvoiceStatus.ISSUED, 2L, "2027-01", InvoiceFormat.ZUGFERD_EN16931, TODAY,
                TODAY, TODAY, 14, null, null, null, null, List.of(), NOW, NOW, 0L);
        when(repository.findById(7L)).thenReturn(Optional.of(issued));
        assertThatThrownBy(() -> service.saveDraft(7, form)).isInstanceOf(InvoiceException.class)
                .hasMessage("Eine ausgestellte Rechnung ändert sich nicht mehr.");
        assertThatThrownBy(() -> service.deleteDraft(7)).isInstanceOf(InvoiceException.class);
        verify(repository, never()).deleteById(7L);
    }

    @Test
    void deletesADraft() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        service.deleteDraft(5);
        verify(repository).deleteById(5L);
        verify(audit).add(AuditAction.DRAFT_DELETED, "Verlag am Fluss GmbH");
    }

    @Test
    void namesTheCustomerOrADash() {
        assertThat(service.customerName(draft(List.of()))).isEqualTo("Verlag am Fluss GmbH");
        when(customers.find(2)).thenReturn(Optional.empty());
        assertThat(service.customerName(draft(List.of()))).isEqualTo("–");
    }

    @Test
    void aCompleteDraftHasNoProblemsAndADocument() {
        Invoice complete = draft(List.of(lump("Lesung", "400", TaxCategory.KLEINUNTERNEHMER)));
        assertThat(service.problems(complete)).isEmpty();
        InvoiceDocument document = service.document(complete).orElseThrow();
        assertThat(document.number()).isEqualTo("Entwurf");
        assertThat(document.total()).isEqualByComparingTo("400.00");
        assertThat(document.seller().name()).isEqualTo("Erika Mustermann");
        assertThat(document.buyer().name()).isEqualTo("Verlag am Fluss GmbH");
        assertThat(document.iban()).isEqualTo(SellerTestData.IBAN);
        assertThat(document.dueDate()).isEqualTo(TODAY.plusDays(14));
        assertThat(document.intro()).isEqualTo("Hallo");
        assertThat(document.closing()).isEqualTo("Danke");
    }

    @Test
    void namesEveryProblemOfAnIncompleteDraft() {
        Invoice empty = draft(List.of());
        assertThat(service.problems(empty)).containsExactly("Die Rechnung braucht mindestens eine Position.");
        assertThat(service.document(empty)).isEmpty();

        Invoice blank = draft(List.of(lump(" ", "0", TaxCategory.KLEINUNTERNEHMER)));
        assertThat(service.problems(blank)).containsExactly("Position 1: Bitte die Leistung beschreiben.",
                "Position 1: Der Betrag ist größer als 0.");
        Invoice backwards = new Invoice(5L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.ZUGFERD_EN16931, TODAY,
                TODAY, TODAY.minusDays(1), 14, null, null, null, null, List.of(lump("Lesung", "1", TaxCategory.KLEINUNTERNEHMER)),
                NOW, NOW, 0L);
        assertThat(service.problems(backwards)).containsExactly("Der Leistungszeitraum endet vor seinem Beginn.");
        Invoice nullText = draft(List.of(new DraftLine(null, true, BigDecimal.ONE, BigDecimal.TEN, TaxCategory.KLEINUNTERNEHMER)));
        assertThat(service.problems(nullText)).containsExactly("Position 1: Bitte die Leistung beschreiben.");
    }

    @Test
    void appliesTheRulesOfTheCategories() {
        assertThat(service.problems(draft(List.of(lump("Ausland", "1", TaxCategory.NOT_TAXABLE_ABROAD),
                lump("Lesung", "1", TaxCategory.KLEINUNTERNEHMER)))))
                .containsExactly("„Nicht steuerbar“ steht allein auf einer Rechnung (EN 16931, BR-O-12).");
        assertThat(service.problems(draft(List.of(lump("Ausland", "1", TaxCategory.NOT_TAXABLE_ABROAD))))).isEmpty();
        assertThat(service.problems(draft(List.of(lump("Buch", "1", TaxCategory.STANDARD_7)))))
                .containsExactly("Als Kleinunternehmer stellen Sie keine Umsatzsteuer in Rechnung (§ 19 UStG).");

        when(sellers.find()).thenReturn(Optional.of(seller(TaxStatus.REGULAR, null)));
        assertThat(service.problems(draft(List.of(lump("Lesung", "1", TaxCategory.KLEINUNTERNEHMER)))))
                .containsExactly("Mit Regelbesteuerung gibt es keine Positionen nach § 19 UStG.");
        assertThat(service.problems(draft(List.of(lump("Buch", "1", TaxCategory.STANDARD_7))))).isEmpty();
    }

    @Test
    void reverseChargeNeedsBothVatIds() {
        Invoice reverse = draft(List.of(lump("Workshop", "600", TaxCategory.REVERSE_CHARGE_EU)));
        String problem = "Reverse Charge braucht die USt-IdNr. von Ihnen und vom Kunden (§ 14a UStG).";
        assertThat(service.problems(reverse)).containsExactly(problem);
        when(sellers.find()).thenReturn(Optional.of(seller(TaxStatus.KLEINUNTERNEHMER, "DE123456789")));
        assertThat(service.problems(reverse)).containsExactly(problem);
        Customer v = CustomerTestData.verlag();
        when(customers.find(2)).thenReturn(Optional.of(new Customer(2L, v.name(), v.street(), v.postcode(), v.city(), "NL",
                null, null, "NL123456789B01", null, v.format())));
        assertThat(service.problems(reverse)).isEmpty();
    }

    @Test
    void xrechnungNeedsABuyerReference() {
        Invoice xrechnung = new Invoice(5L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.XRECHNUNG, TODAY, TODAY, TODAY,
                14, null, null, null, null, List.of(lump("Kurs", "1", TaxCategory.KLEINUNTERNEHMER)), NOW, NOW, 0L);
        assertThat(service.problems(xrechnung))
                .containsExactly("Die XRechnung braucht eine Käuferreferenz, zum Beispiel die Leitweg-ID (BR-DE-15).");
        when(customers.find(2)).thenReturn(Optional.of(CustomerTestData.vhs().withId(2L)));
        Invoice withReference = new Invoice(5L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.ZUGFERD_XRECHNUNG, TODAY,
                TODAY, TODAY, 14, "04011000-12345-67", null, null, null,
                List.of(lump("Kurs", "1", TaxCategory.KLEINUNTERNEHMER)), NOW, NOW, 0L);
        assertThat(service.problems(withReference)).isEmpty();

        Customer v = CustomerTestData.verlag();
        when(customers.find(2)).thenReturn(Optional.of(new Customer(2L, v.name(), v.street(), v.postcode(), v.city(),
                v.countryCode(), null, null, null, null, v.format())));
        assertThat(service.problems(withReference))
                .containsExactly("Die XRechnung braucht die E-Mail-Adresse des Kunden. Bitte beim Kunden eintragen.");
        when(customers.find(2)).thenReturn(Optional.empty());
        assertThat(service.problems(withReference)).containsExactly("Den Kunden gibt es nicht mehr.");
    }

    @Test
    void needsTheProfileAndTheCustomerForTheIssue() {
        when(sellers.find()).thenReturn(Optional.empty());
        when(customers.find(2)).thenReturn(Optional.empty());
        assertThat(service.problems(draft(List.of(lump("Lesung", "1", TaxCategory.KLEINUNTERNEHMER)))))
                .containsExactly("Das Profil in den Einstellungen fehlt.", "Den Kunden gibt es nicht mehr.");
    }

    @Test
    void refusesAFormOfAnOlderVersion() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        DraftForm stale = form();
        stale.setVersion(-1L);
        InvoiceException e = catchThrowableOfType(InvoiceException.class, () -> service.saveDraft(5, stale));
        assertThat(e.problems()).containsExactly(
                "Der Entwurf wurde inzwischen in einem anderen Fenster geändert. Bitte die Seite neu laden.");
        DraftForm missing = form();
        missing.setVersion(null);
        assertThatThrownBy(() -> service.saveDraft(5, missing)).isInstanceOf(InvoiceException.class);
        verify(repository, never()).save(any());
        assertThat(service.saveDraft(5, form()).version()).isZero();
    }

    @Test
    void refusesAnAmbiguousAndATooLargeNumber() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        DraftForm form = form(row("Lesung", true, "1", "1.500", TaxCategory.KLEINUNTERNEHMER),
                row("Kurs", false, "1234567890123", "999999999999,9999", TaxCategory.KLEINUNTERNEHMER));
        InvoiceException e = catchThrowableOfType(InvoiceException.class, () -> service.saveDraft(5, form));
        assertThat(e.problems()).containsExactly(
                "Position 1: Der Preis ist mehrdeutig: bitte mit Komma schreiben, zum Beispiel 1.500,00 oder 1,5.",
                "Position 2: Die Menge hat höchstens 12 Stellen vor dem Komma.");
    }

    @Test
    void aLineThatTurnsIntoALumpSumKeepsItsAmount() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        Invoice saved = service.saveDraft(5, form(row("Unterricht", true, "16", "45,00", TaxCategory.EXEMPT_4_21),
                row("Kurs", true, "1,5", "33,33", TaxCategory.EXEMPT_4_21)));
        assertThat(saved.lines().get(0).unitPrice()).isEqualByComparingTo("720.00");
        assertThat(saved.lines().get(0).quantity()).isEqualByComparingTo("1");
        assertThat(saved.lines().get(1).unitPrice()).isEqualByComparingTo("50.00");
    }

    @Test
    void keepsTheAmountOfALumpSumWithoutAQuantity() {
        BigDecimal price = new BigDecimal("400");
        assertThat(InvoiceService.keptAmount(BigDecimal.ONE, price)).isSameAs(price);
        assertThat(InvoiceService.keptAmount(new BigDecimal("1.0000"), price)).isSameAs(price);
        assertThat(InvoiceService.keptAmount(BigDecimal.ZERO, price)).isSameAs(price);
        assertThat(InvoiceService.keptAmount(new BigDecimal("-2"), price)).isSameAs(price);
        assertThat(InvoiceService.keptAmount(null, price)).isSameAs(price);
        assertThat(InvoiceService.keptAmount(BigDecimal.TEN, null)).isNull();
        assertThat(InvoiceService.keptAmount(new BigDecimal("2"), price)).isEqualByComparingTo("800.00");
    }

    @Test
    void readsAnEmptyPriceAndQuantityAsZero() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        Invoice saved = service.saveDraft(5, form(row("Lesung", false, " ", "", TaxCategory.KLEINUNTERNEHMER),
                row("Reise", true, "1", null, TaxCategory.KLEINUNTERNEHMER)));
        assertThat(saved.lines()).extracting(DraftLine::unitPrice).allSatisfy(price -> assertThat(price).isZero());
        assertThat(saved.lines().getFirst().quantity()).isZero();
        assertThat(service.problems(saved)).contains("Position 1: Der Betrag ist größer als 0.",
                "Position 2: Der Betrag ist größer als 0.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0,01"})
    void acceptsAnyPositiveAmount(String amount) {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        Invoice saved = service.saveDraft(5, form(row("Lesung", true, "1", amount, TaxCategory.KLEINUNTERNEHMER)));
        assertThat(service.problems(saved)).isEmpty();
    }

    @Test
    void keepsTheEnteredOrderOfTheLines() {
        when(repository.findById(5L)).thenReturn(Optional.of(draft(List.of())));
        List<LineForm> rows = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            rows.add(row("Position " + i, true, "1", String.valueOf(i), TaxCategory.KLEINUNTERNEHMER));
        }
        Invoice saved = service.saveDraft(5, form(rows.toArray(LineForm[]::new)));
        assertThat(saved.lines()).extracting(DraftLine::text).containsExactly("Position 1", "Position 2", "Position 3");
        DraftForm empty = form();
        empty.setLines(new ArrayList<>(List.of(row(null, true, "1", "5", TaxCategory.KLEINUNTERNEHMER))));
        assertThat(service.saveDraft(5, empty).lines()).extracting(DraftLine::text).containsExactly("");
    }
}
