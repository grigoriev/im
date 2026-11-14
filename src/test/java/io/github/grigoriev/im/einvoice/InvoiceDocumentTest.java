package io.github.grigoriev.im.einvoice;

import static io.github.grigoriev.im.einvoice.SampleInvoices.DAY;
import static io.github.grigoriev.im.einvoice.SampleInvoices.DUTCH;
import static io.github.grigoriev.im.einvoice.SampleInvoices.PUBLISHER;
import static io.github.grigoriev.im.einvoice.SampleInvoices.SELLER;
import static io.github.grigoriev.im.einvoice.SampleInvoices.SELLER_EU;
import static io.github.grigoriev.im.einvoice.SampleInvoices.SWISS;
import static io.github.grigoriev.im.einvoice.SampleInvoices.invoice;
import static io.github.grigoriev.im.einvoice.SampleInvoices.line;
import static io.github.grigoriev.im.einvoice.TaxCategory.EXEMPT_4_21;
import static io.github.grigoriev.im.einvoice.TaxCategory.KLEINUNTERNEHMER;
import static io.github.grigoriev.im.einvoice.TaxCategory.NOT_TAXABLE_ABROAD;
import static io.github.grigoriev.im.einvoice.TaxCategory.REVERSE_CHARGE_EU;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class InvoiceDocumentTest {

    private static final InvoiceLine KU = line("Lesung", "1", "400.00", KLEINUNTERNEHMER);
    private static final InvoiceLine EX = line("Unterricht", "16", "45.00", EXEMPT_4_21);
    private static final InvoiceLine OUT = line("Lesung", "1", "350.00", NOT_TAXABLE_ABROAD);
    private static final InvoiceLine RC = line("Workshop", "1", "600.00", REVERSE_CHARGE_EU);
    private static final InvoiceFormat ZUGFERD = InvoiceFormat.ZUGFERD_EN16931;

    private static InvoiceDocument withNote(String note, InvoiceLine... lines) {
        return new InvoiceDocument("2027-10", DAY, DAY, DAY, DAY, SELLER, PUBLISHER, null, "DE02", "BIC",
                null, null, note, ZUGFERD, List.of(lines));
    }

    private static InvoiceDocument build(String number, LocalDate issue, LocalDate from, LocalDate to, LocalDate due,
            Party seller, Party buyer, String iban, String bic, InvoiceFormat format) {
        return new InvoiceDocument(number, issue, from, to, due, seller, buyer, null, iban, bic, null, null, null,
                format, List.of(KU));
    }

    @Test
    void showsTheTaxColumnOnlyForSeveralCategories() {
        InvoiceLine lump = SampleInvoices.lump("Lesung", "400.00", KLEINUNTERNEHMER);
        assertThat(invoice(PUBLISHER, null, ZUGFERD, lump, KU).showsTaxColumn()).isFalse();
        assertThat(invoice(PUBLISHER, null, ZUGFERD, lump).columns()).isEqualTo(2);
        assertThat(invoice(PUBLISHER, null, ZUGFERD, KU).columns()).isEqualTo(4);
        InvoiceDocument mixed = invoice(PUBLISHER, null, ZUGFERD, lump, EX);
        assertThat(mixed.showsTaxColumn()).isTrue();
        assertThat(mixed.columns()).isEqualTo(5);
        InvoiceDocument mixedLumps = invoice(PUBLISHER, null, ZUGFERD, lump, SampleInvoices.lump("x", "1", EXEMPT_4_21));
        assertThat(mixedLumps.columns()).isEqualTo(3);
    }

    @Test
    void showsQuantitiesOnlyWhenALineHasItsOwn() {
        InvoiceLine lump = SampleInvoices.lump("Lesung", "400.00", KLEINUNTERNEHMER);
        assertThat(invoice(PUBLISHER, null, ZUGFERD, lump, lump).showsQuantities()).isFalse();
        assertThat(invoice(PUBLISHER, null, ZUGFERD, lump, EX).showsQuantities()).isTrue();
        assertThat(invoice(PUBLISHER, null, ZUGFERD, EX).showsQuantities()).isTrue();
    }

    @Test
    void sumsTheLinesWithoutVat() {
        InvoiceDocument document = invoice(PUBLISHER, null, ZUGFERD, KU, EX);
        assertThat(document.netTotal()).isEqualTo(new BigDecimal("1120.00"));
        assertThat(document.vatAmounts()).isEmpty();
        assertThat(document.vatTotal()).isEqualByComparingTo("0");
        assertThat(document.total()).isEqualTo(new BigDecimal("1120.00"));
    }

    @Test
    void computesTheVatPerRateHighestFirst() {
        InvoiceDocument sale = SampleInvoices.sale(ZUGFERD);
        assertThat(sale.netTotal()).isEqualTo(new BigDecimal("130.50"));
        assertThat(sale.vatAmounts()).containsExactly(
                new VatAmount(new BigDecimal("19"), new BigDecimal("55.80"), new BigDecimal("10.60")),
                new VatAmount(new BigDecimal("7"), new BigDecimal("74.70"), new BigDecimal("5.23")));
        assertThat(sale.vatTotal()).isEqualTo(new BigDecimal("15.83"));
        assertThat(sale.total()).isEqualTo(new BigDecimal("146.33"));
    }

    @Test
    void roundsTheVatOnceOnTheSumOfARate() {
        InvoiceLine cents = line("Kleinteil", "1", "0.03", TaxCategory.STANDARD_19);
        InvoiceDocument document = invoice(SampleInvoices.SHOP, PUBLISHER, null, ZUGFERD, cents, cents, cents);
        assertThat(document.vatAmounts()).containsExactly(
                new VatAmount(new BigDecimal("19"), new BigDecimal("0.09"), new BigDecimal("0.02")));
    }

    @Test
    void rejectsVatOnAKleinunternehmerInvoice() {
        InvoiceLine vat = line("Buch", "1", "20.00", TaxCategory.STANDARD_7);
        assertThatIllegalArgumentException().isThrownBy(() -> invoice(PUBLISHER, null, ZUGFERD, KU, vat))
                .withMessageContaining("§19 UStG");
    }

    @Test
    void acceptsVatNextToExemptAndReverseChargeLines() {
        InvoiceLine vat = line("Buch", "1", "20.00", TaxCategory.STANDARD_19);
        assertThat(invoice(SampleInvoices.SHOP, SampleInvoices.SCHOOL, null, ZUGFERD, vat, EX).categories()).hasSize(2);
        assertThat(invoice(SampleInvoices.SHOP, DUTCH, null, ZUGFERD, vat, RC).categories()).hasSize(2);
    }

    @Test
    void printsNoTaxNoteForVatOnlyAndOnlyTheExemptNoteNextToVat() {
        InvoiceLine vat = line("Buch", "1", "20.00", TaxCategory.STANDARD_19);
        assertThat(invoice(SampleInvoices.SHOP, PUBLISHER, null, ZUGFERD, vat).effectiveTaxNote()).isEmpty();
        assertThat(invoice(SampleInvoices.SHOP, PUBLISHER, null, ZUGFERD, vat, EX).effectiveTaxNote())
                .isEqualTo(EXEMPT_4_21.defaultNote());
    }

    @Test
    void listsCategoriesInTheOrderOfTheirFirstLine() {
        assertThat(invoice(PUBLISHER, null, ZUGFERD, EX, KU, EX).categories())
                .containsExactly(EXEMPT_4_21, KLEINUNTERNEHMER);
    }

    @Test
    void joinsTheDefaultNotesOfTheCategoriesUsed() {
        assertThat(withNote(null, KU, EX, KU).effectiveTaxNote())
                .isEqualTo(KLEINUNTERNEHMER.defaultNote() + "\n" + EXEMPT_4_21.defaultNote());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void fallsBackToTheDefaultNoteWithoutAnOwnNote(String note) {
        assertThat(withNote(note, KU).effectiveTaxNote()).isEqualTo(KLEINUNTERNEHMER.defaultNote());
    }

    @Test
    void prefersTheOwnNote() {
        assertThat(withNote("Eigener Hinweis", KU).effectiveTaxNote()).isEqualTo("Eigener Hinweis");
    }

    @Test
    void copiesTheLines() {
        List<InvoiceLine> lines = new ArrayList<>(List.of(KU));
        InvoiceDocument document = new InvoiceDocument("1", DAY, DAY, DAY, DAY, SELLER, PUBLISHER, null, "I", "B",
                null, null, null, ZUGFERD, lines);
        lines.add(EX);
        assertThat(document.lines()).containsExactly(KU);
    }

    @Test
    void rejectsAnInvoiceWithoutLines() {
        assertThatIllegalArgumentException().isThrownBy(() -> withNote(null))
                .withMessageContaining("at least one line");
    }

    @Test
    void rejectsAServicePeriodThatEndsBeforeItStarts() {
        LocalDate before = DAY.minusDays(1);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> build("1", DAY, DAY, before, DAY, SELLER, PUBLISHER, "I", "B", ZUGFERD))
                .withMessageContaining("ends before it starts");
    }

    @Test
    void acceptsAOneDayServicePeriod() {
        assertThat(withNote(null, KU).serviceTo()).isEqualTo(DAY);
    }

    @Test
    void rejectsCategoryOMixedWithAnotherCategory() {
        assertThatIllegalArgumentException().isThrownBy(() -> invoice(SWISS, null, ZUGFERD, OUT, KU))
                .withMessageContaining("BR-O-12");
        assertThatIllegalArgumentException().isThrownBy(() -> invoice(SWISS, null, ZUGFERD, RC, OUT))
                .withMessageContaining("BR-O-12");
    }

    @Test
    void acceptsCategoryOAlone() {
        assertThat(invoice(SWISS, null, ZUGFERD, OUT, OUT).categories()).containsExactly(NOT_TAXABLE_ABROAD);
    }

    @Test
    void acceptsMixedExemptCategories() {
        assertThat(invoice(SELLER_EU, DUTCH, null, ZUGFERD, KU, EX, RC).categories()).hasSize(3);
    }

    @Test
    void rejectsReverseChargeWithoutBothVatIds() {
        assertThatIllegalArgumentException().isThrownBy(() -> invoice(SELLER, DUTCH, null, ZUGFERD, RC))
                .withMessageContaining("§14a UStG");
        assertThatIllegalArgumentException().isThrownBy(() -> invoice(SELLER_EU, PUBLISHER, null, ZUGFERD, RC))
                .withMessageContaining("§14a UStG");
    }

    @Test
    void needsNoVatIdWithoutReverseCharge() {
        assertThat(invoice(SELLER, PUBLISHER, null, ZUGFERD, KU, EX).seller().vatId()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsXRechnungWithoutBuyerReference(String reference) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> invoice(PUBLISHER, reference, InvoiceFormat.XRECHNUNG, KU))
                .withMessageContaining("BR-DE-15");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> invoice(PUBLISHER, reference, InvoiceFormat.ZUGFERD_XRECHNUNG, KU))
                .withMessageContaining("BR-DE-15");
    }

    @Test
    void acceptsXRechnungWithBuyerReferenceAndZugferdWithout() {
        assertThat(invoice(PUBLISHER, "04011000-12345-67", InvoiceFormat.XRECHNUNG, KU).buyerReference())
                .isNotBlank();
        assertThat(invoice(PUBLISHER, null, ZUGFERD, KU).buyerReference()).isNull();
    }

    @Test
    void requiresTheMandatoryFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> build(null, DAY, DAY, DAY, DAY, SELLER, PUBLISHER, "I", "B", ZUGFERD))
                .withMessage("number");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", null, DAY, DAY, DAY, SELLER, PUBLISHER, "I", "B", ZUGFERD))
                .withMessage("issueDate");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", DAY, null, DAY, DAY, SELLER, PUBLISHER, "I", "B", ZUGFERD))
                .withMessage("serviceFrom");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", DAY, DAY, null, DAY, SELLER, PUBLISHER, "I", "B", ZUGFERD))
                .withMessage("serviceTo");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", DAY, DAY, DAY, null, SELLER, PUBLISHER, "I", "B", ZUGFERD))
                .withMessage("dueDate");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", DAY, DAY, DAY, DAY, null, PUBLISHER, "I", "B", ZUGFERD))
                .withMessage("seller");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", DAY, DAY, DAY, DAY, SELLER, null, "I", "B", ZUGFERD))
                .withMessage("buyer");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", DAY, DAY, DAY, DAY, SELLER, PUBLISHER, null, "B", ZUGFERD))
                .withMessage("iban");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", DAY, DAY, DAY, DAY, SELLER, PUBLISHER, "I", null, ZUGFERD))
                .withMessage("bic");
        assertThatNullPointerException()
                .isThrownBy(() -> build("1", DAY, DAY, DAY, DAY, SELLER, PUBLISHER, "I", "B", null))
                .withMessage("format");
    }

    @Test
    void knowsAStornorechnung() {
        assertThat(SampleInvoices.reading(ZUGFERD).isCancellation()).isFalse();
        InvoiceDocument cancellation = SampleInvoices.cancellation(ZUGFERD);
        assertThat(cancellation.isCancellation()).isTrue();
        assertThat(cancellation.cancels()).isEqualTo(new InvoiceReference("2027-07", DAY.minusDays(10)));
        assertThatThrownBy(() -> new InvoiceReference(null, DAY)).hasMessage("number");
        assertThatThrownBy(() -> new InvoiceReference("1", null)).hasMessage("issueDate");
    }
}
