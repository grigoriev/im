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

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mustangproject.BankDetails;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;

class MustangInvoiceMapperTest {

    private final MustangInvoiceMapper mapper = new MustangInvoiceMapper(ZoneOffset.UTC);

    private static Product product(Invoice invoice, int index) {
        return ((Item) invoice.getZFItems()[index]).getProduct();
    }

    private static Date utc(java.time.LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void mapsHeaderDatesAndPaymentTerms() {
        Invoice invoice = mapper.toMustang(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(invoice.getNumber()).isEqualTo("2027-10");
        assertThat(invoice.getCurrency()).isEqualTo("EUR");
        assertThat(invoice.getIssueDate()).isEqualTo(utc(DAY));
        assertThat(invoice.getDueDate()).isEqualTo(utc(DAY.plusDays(14)));
        assertThat(invoice.getDeliveryDate()).isEqualTo(utc(DAY.minusDays(2)));
        assertThat(invoice.getDetailedDeliveryPeriodFrom()).isEqualTo(utc(DAY.minusDays(2)));
        assertThat(invoice.getDetailedDeliveryPeriodTo()).isEqualTo(utc(DAY.minusDays(2)));
        assertThat(invoice.getPaymentTermDescription()).isEqualTo("Zahlbar bis 2027-04-01");
        assertThat(invoice.getReferenceNumber()).isNull();
    }

    @Test
    void mapsTheBuyerReference() {
        Invoice invoice = mapper.toMustang(SampleInvoices.reading(InvoiceFormat.XRECHNUNG));
        assertThat(invoice.getReferenceNumber()).isEqualTo("04011000-12345-67");
    }

    @Test
    void mapsTheSellerWithContactBankAndTaxNumberAsIdentifier() {
        TradeParty seller = mapper.toMustang(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931)).getSender();
        assertThat(seller.getName()).isEqualTo("Elena Sommer");
        assertThat(seller.getStreet()).isEqualTo("Lindenstraße 12");
        assertThat(seller.getZIP()).isEqualTo("80331");
        assertThat(seller.getLocation()).isEqualTo("München");
        assertThat(seller.getCountry()).isEqualTo("DE");
        assertThat(seller.getTaxID()).isEqualTo("143/123/45678");
        assertThat(seller.getID()).isEqualTo("143/123/45678");
        assertThat(seller.getVATID()).isNull();
        assertThat(seller.getUriUniversalCommunicationID()).isEqualTo("kontakt@sommer.example");
        assertThat(seller.getContact().getName()).isEqualTo("Elena Sommer");
        assertThat(seller.getContact().getPhone()).isEqualTo("+49 89 1234567");
        assertThat(seller.getContact().getEMail()).isEqualTo("kontakt@sommer.example");
        BankDetails bank = seller.getBankDetails().getFirst();
        assertThat(bank.getIBAN()).isEqualTo("DE02120300000000202051");
        assertThat(bank.getBIC()).isEqualTo("BYLADEM1001");
        assertThat(bank.getPaymentMeansCode()).isEqualTo("58");
    }

    @Test
    void keepsTheVatIdAsSellerIdentifierWhenPresent() {
        Party withVat = new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE", null, null, null,
                "143/123/45678", "DE123456789");
        InvoiceDocument document = new InvoiceDocument("1", DAY, DAY, DAY, DAY, withVat, PUBLISHER, null, "I", "B",
                null, null, null, InvoiceFormat.ZUGFERD_EN16931, List.of(line("x", "1", "1", KLEINUNTERNEHMER)));
        TradeParty seller = mapper.toMustang(document).getSender();
        assertThat(seller.getVATID()).isEqualTo("DE123456789");
        assertThat(seller.getTaxID()).isEqualTo("143/123/45678");
        assertThat(seller.getID()).isNull();
        assertThat(seller.getUriUniversalCommunicationID()).isNull();
    }

    @Test
    void omitsTheTaxNumberWhenMissing() {
        Party noTax = new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE", null, null, null, null,
                "DE123456789");
        InvoiceDocument document = new InvoiceDocument("1", DAY, DAY, DAY, DAY, noTax, PUBLISHER, null, "I", "B",
                null, null, null, InvoiceFormat.ZUGFERD_EN16931, List.of(line("x", "1", "1", KLEINUNTERNEHMER)));
        TradeParty seller = mapper.toMustang(document).getSender();
        assertThat(seller.getTaxID()).isNull();
        assertThat(seller.getID()).isNull();
    }

    @Test
    void mapsTheBuyerWithVatId() {
        TradeParty buyer = mapper.toMustang(invoice(SELLER_EU, DUTCH, null, InvoiceFormat.ZUGFERD_EN16931,
                line("Workshop", "1", "600.00", REVERSE_CHARGE_EU))).getRecipient();
        assertThat(buyer.getName()).isEqualTo("Lingua Lab B.V.");
        assertThat(buyer.getCountry()).isEqualTo("NL");
        assertThat(buyer.getVATID()).isEqualTo("NL123456789B01");
        assertThat(buyer.getUriUniversalCommunicationID()).isEqualTo("invoices@lingualab.example");
    }

    @Test
    void mapsLinesWithUnitPriceAndQuantity() {
        Invoice invoice = mapper.toMustang(invoice(PUBLISHER, null, InvoiceFormat.ZUGFERD_EN16931,
                line("Lektorat", "120", "4.50", KLEINUNTERNEHMER)));
        Item item = (Item) invoice.getZFItems()[0];
        assertThat(item.getPrice()).isEqualByComparingTo("4.50");
        assertThat(item.getQuantity()).isEqualByComparingTo("120");
        assertThat(item.getProduct().getName()).isEqualTo("Lektorat");
        assertThat(item.getProduct().getUnit()).isEqualTo("C62");
        assertThat(item.getProduct().getVATPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getProduct().getTaxCategoryCode()).isEqualTo("E");
    }

    @Test
    void joinsBothExemptNotesIntoOneReasonForMixedCategoryE() {
        Invoice invoice = mapper.toMustang(invoice(PUBLISHER, null, InvoiceFormat.ZUGFERD_EN16931,
                line("Unterricht", "16", "45.00", EXEMPT_4_21), line("Lesung", "1", "400.00", KLEINUNTERNEHMER)));
        String joined = EXEMPT_4_21.defaultNote() + " " + KLEINUNTERNEHMER.defaultNote();
        assertThat(product(invoice, 0).getTaxExemptionReason()).isEqualTo(joined);
        assertThat(product(invoice, 1).getTaxExemptionReason()).isEqualTo(joined);
        assertThat(product(invoice, 0).getTaxExemptionReasonCode()).isNull();
    }

    @Test
    void mapsReverseChargeWithItsCode() {
        Invoice invoice = mapper.toMustang(invoice(SELLER_EU, DUTCH, null, InvoiceFormat.ZUGFERD_EN16931,
                line("Workshop", "1", "600.00", REVERSE_CHARGE_EU), line("Lesung", "1", "1", KLEINUNTERNEHMER)));
        assertThat(product(invoice, 0).getTaxCategoryCode()).isEqualTo("AE");
        assertThat(product(invoice, 0).getTaxExemptionReason()).isEqualTo(REVERSE_CHARGE_EU.defaultNote());
        assertThat(product(invoice, 0).getTaxExemptionReasonCode()).isEqualTo("VATEX-EU-AE");
        assertThat(product(invoice, 1).getTaxExemptionReason()).isEqualTo(KLEINUNTERNEHMER.defaultNote());
    }

    @Test
    void mapsNotTaxableWithItsCode() {
        Invoice invoice = mapper.toMustang(invoice(SWISS, null, InvoiceFormat.ZUGFERD_EN16931,
                line("Lesung", "1", "350.00", NOT_TAXABLE_ABROAD)));
        assertThat(product(invoice, 0).getTaxCategoryCode()).isEqualTo("O");
        assertThat(product(invoice, 0).getTaxExemptionReasonCode()).isEqualTo("VATEX-EU-O");
        assertThat(product(invoice, 0).getTaxExemptionReason()).isEqualTo(NOT_TAXABLE_ABROAD.defaultNote());
    }

    @Test
    void usesTheOwnNoteAsReasonForEveryCategory() {
        InvoiceDocument document = new InvoiceDocument("1", DAY, DAY, DAY, DAY, SELLER_EU, DUTCH, null, "I", "B", null,
                null, "Eigener Hinweis", InvoiceFormat.ZUGFERD_EN16931,
                List.of(line("Workshop", "1", "1", REVERSE_CHARGE_EU), line("Lesung", "1", "1", KLEINUNTERNEHMER)));
        Invoice invoice = mapper.toMustang(document);
        assertThat(product(invoice, 0).getTaxExemptionReason()).isEqualTo("Eigener Hinweis");
        assertThat(product(invoice, 1).getTaxExemptionReason()).isEqualTo("Eigener Hinweis");
    }

    @Test
    void ignoresABlankOwnNote() {
        InvoiceDocument document = new InvoiceDocument("1", DAY, DAY, DAY, DAY, SELLER_EU, DUTCH, null, "I", "B", null,
                null, " ", InvoiceFormat.ZUGFERD_EN16931,
                List.of(line("Workshop", "1", "1", REVERSE_CHARGE_EU), line("Lesung", "1", "1", KLEINUNTERNEHMER)));
        Invoice invoice = mapper.toMustang(document);
        assertThat(product(invoice, 0).getTaxExemptionReason()).isEqualTo(REVERSE_CHARGE_EU.defaultNote());
        assertThat(product(invoice, 1).getTaxExemptionReason()).isEqualTo(KLEINUNTERNEHMER.defaultNote());
    }

    @Test
    void convertsDatesInTheSystemZoneByDefault() {
        Invoice invoice = new MustangInvoiceMapper().toMustang(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(invoice.getIssueDate()).isEqualTo(Date.from(DAY.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }
}
