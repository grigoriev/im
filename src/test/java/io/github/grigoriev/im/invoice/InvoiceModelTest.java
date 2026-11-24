package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.einvoice.CustomerReferences;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.InvoiceLine;
import io.github.grigoriev.im.einvoice.LineUnit;
import io.github.grigoriev.im.einvoice.TaxCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceModelTest {

    private static Invoice invoice(InvoiceStatus status, List<DraftLine> lines) {
        LocalDate day = LocalDate.of(2027, 3, 18);
        return new Invoice(1L, status, 2L, status == InvoiceStatus.DRAFT ? null : "2027-01",
                InvoiceFormat.ZUGFERD_EN16931, day, day, day, 14, null, null, null, null, lines, Instant.EPOCH, Instant.EPOCH, 0L, null, null, null);
    }

    @Test
    void readsNoReferencesAsNone() {
        Invoice i = invoice(InvoiceStatus.DRAFT, List.of());
        assertThat(i.references()).isEqualTo(CustomerReferences.NONE);
        assertThat(new Invoice(i.id(), i.status(), i.customerId(), i.number(), i.format(), i.issueDate(), i.serviceFrom(),
                i.serviceTo(), i.paymentDays(), i.buyerReference(), i.intro(), i.closing(), i.taxNote(), i.lines(),
                i.createdAt(), i.updatedAt(), i.version(), i.issuedAt(), i.snapshot(), i.activity(), null).references())
                .as("the columns of a row without references").isEqualTo(CustomerReferences.NONE);
    }

    @Test
    void isDueAfterThePaymentDays() {
        assertThat(invoice(InvoiceStatus.DRAFT, List.of()).dueDate()).isEqualTo(LocalDate.of(2027, 4, 1));
    }

    @Test
    void onlyADraftChanges() {
        assertThat(invoice(InvoiceStatus.DRAFT, List.of()).isDraft()).isTrue();
        assertThat(invoice(InvoiceStatus.ISSUED, List.of()).isDraft()).isFalse();
    }

    @Test
    void keepsACopyOfItsLines() {
        List<DraftLine> lines = new ArrayList<>(List.of(DraftLine.empty(TaxCategory.KLEINUNTERNEHMER)));
        Invoice invoice = invoice(InvoiceStatus.DRAFT, lines);
        lines.clear();
        assertThat(invoice.lines()).hasSize(1);
        List<DraftLine> stored = invoice.lines();
        assertThatThrownBy(stored::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anEmptyLineIsALumpSumOfZero() {
        DraftLine empty = DraftLine.empty(TaxCategory.STANDARD_19);
        assertThat(empty.text()).isEmpty();
        assertThat(empty.lumpSum()).isTrue();
        assertThat(empty.quantity()).isEqualByComparingTo("1");
        assertThat(empty.unitPrice()).isEqualByComparingTo("0");
        assertThat(empty.category()).isEqualTo(TaxCategory.STANDARD_19);
    }

    @Test
    void storesQuantityAndPriceWithFourDecimals() {
        DraftLine line = new DraftLine("Kurs", false, new BigDecimal("16"), new BigDecimal("45.5"), TaxCategory.EXEMPT_4_21);
        assertThat(line.quantity()).isEqualTo(new BigDecimal("16.0000"));
        assertThat(line.unitPrice()).isEqualTo(new BigDecimal("45.5000"));
        assertThat(line).isEqualTo(new DraftLine("Kurs", false, new BigDecimal("16.0000"), new BigDecimal("45.50"),
                TaxCategory.EXEMPT_4_21));
        assertThat(new DraftLine("Kurs", true, null, null, TaxCategory.EXEMPT_4_21).unitPrice()).isNull();
        BigDecimal tooFine = new BigDecimal("0.00001");
        assertThatThrownBy(() -> new DraftLine("Kurs", true, BigDecimal.ONE, tooFine, TaxCategory.EXEMPT_4_21))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void becomesALineOfTheInvoiceDocument() {
        InvoiceLine lump = new DraftLine("Lesung", true, BigDecimal.ONE, new BigDecimal("400"), TaxCategory.KLEINUNTERNEHMER)
                .toInvoiceLine();
        assertThat(lump.lumpSum()).isTrue();
        assertThat(lump.netAmount()).isEqualByComparingTo("400.00");
        InvoiceLine counted = new DraftLine("Unterricht", false, new BigDecimal("16"), new BigDecimal("45"),
                TaxCategory.EXEMPT_4_21).toInvoiceLine();
        assertThat(counted.lumpSum()).isFalse();
        assertThat(counted.netAmount()).isEqualByComparingTo("720.00");
        assertThat(counted.text()).isEqualTo("Unterricht");
        assertThat(counted.category()).isEqualTo(TaxCategory.EXEMPT_4_21);
    }

    @Test
    void labelsEveryStatusInGerman() {
        assertThat(InvoiceStatus.DRAFT.label()).isEqualTo("Entwurf");
        assertThat(InvoiceStatus.ISSUED.label()).isEqualTo("Ausgestellt");
        assertThat(InvoiceStatus.SENT.label()).isEqualTo("Versendet");
        assertThat(InvoiceStatus.PAID.label()).isEqualTo("Bezahlt");
        assertThat(InvoiceStatus.CANCELLED.label()).isEqualTo("Storniert");
    }

    @Test
    void theExceptionKeepsACopyOfTheProblems() {
        List<String> problems = new ArrayList<>(List.of("Eins.", "Zwei."));
        InvoiceException exception = new InvoiceException(problems);
        problems.clear();
        assertThat(exception.problems()).containsExactly("Eins.", "Zwei.");
        assertThat(exception.getMessage()).isEqualTo("Eins. Zwei.");
    }

    @Test
    void aLineKeepsItsUnitAndItsPeriod() {
        LocalDate from = LocalDate.of(2027, 3, 9);
        LocalDate to = LocalDate.of(2027, 3, 20);
        DraftLine line = new DraftLine("Kurs", false, new BigDecimal("24"), new BigDecimal("40"), TaxCategory.EXEMPT_4_21,
                LineUnit.TEACHING_UNIT, from, to);
        InvoiceLine counted = line.toInvoiceLine();
        assertThat(counted.unit()).isEqualTo(LineUnit.TEACHING_UNIT);
        assertThat(counted.serviceFrom()).isEqualTo(from);
        assertThat(counted.serviceTo()).isEqualTo(to);
        InvoiceLine lump = new DraftLine("Kurs", true, BigDecimal.ONE, new BigDecimal("960"), TaxCategory.EXEMPT_4_21,
                LineUnit.HOUR, from, from).toInvoiceLine();
        assertThat(lump.unitCode()).as("a lump sum keeps LS").isEqualTo("LS");
        assertThat(lump.unit()).isEqualTo(LineUnit.PIECE);
        assertThat(lump.serviceTo()).isEqualTo(from);
        DraftLine older = new DraftLine("Kurs", true, BigDecimal.ONE, BigDecimal.ONE, TaxCategory.EXEMPT_4_21);
        assertThat(older.unit()).isEqualTo(LineUnit.PIECE);
        assertThat(older.serviceFrom()).isNull();
        assertThat(older.serviceTo()).isNull();
        assertThat(new DraftLine("Kurs", true, BigDecimal.ONE, BigDecimal.ONE, TaxCategory.EXEMPT_4_21, null, null, null))
                .as("a row from before the units").isEqualTo(older);
        assertThat(DraftLine.empty(TaxCategory.EXEMPT_4_21).unit()).isEqualTo(LineUnit.PIECE);
    }
}
