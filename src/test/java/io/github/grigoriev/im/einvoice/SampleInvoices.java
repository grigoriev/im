package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Fictitious invoices for the tests. No real person or company. */
public final class SampleInvoices {

    static final Party SELLER = new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE",
            "kontakt@sommer.example", "+49 89 1234567", "Elena Sommer", "143/123/45678", null);
    /** The seller without e-mail and phone: ZUGFeRD EN16931 needs neither, only XRechnung does (BR-DE-6, BR-DE-7). */
    static final Party SELLER_NO_CONTACT = new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE",
            null, null, "Elena Sommer", "143/123/45678", null);
    /** The seller with a VAT ID, as reverse charge needs it (§14a UStG). */
    static final Party SELLER_EU = new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE",
            "kontakt@sommer.example", "+49 89 1234567", "Elena Sommer", "143/123/45678", "DE123456789");
    /** A shop that charges VAT: the open-source use beyond the freelancer. */
    static final Party SHOP = new Party("Musterhandel GmbH", "Marktstraße 1", "10115", "Berlin", "DE",
            "rechnung@musterhandel.example", "+49 30 1234567", "Max Mustermann", "30/123/45678", "DE111111111");
    static final Party PUBLISHER = new Party("Verlag am Fluss GmbH", "Hafenweg 3", "20457", "Hamburg", "DE",
            "buchhaltung@verlag-am-fluss.example", null, null, null, null);
    static final Party SCHOOL = new Party("Volkshochschule Musterstadt", "Marktplatz 1", "86150", "Musterstadt", "DE",
            "erechnung@vhs-musterstadt.example", null, null, null, null);
    static final Party DUTCH = new Party("Lingua Lab B.V.", "Oudegracht 10", "3511", "Utrecht", "NL",
            "invoices@lingualab.example", null, null, null, "NL123456789B01");
    static final Party SWISS = new Party("Kulturforum Zürich AG", "Bahnhofstrasse 5", "8001", "Zürich", "CH",
            "kreditoren@kulturforum.example", null, null, null, null);

    static final LocalDate DAY = LocalDate.of(2027, 3, 18);

    private SampleInvoices() {
    }

    static InvoiceLine line(String text, String quantity, String price, TaxCategory category) {
        return InvoiceLine.counted(text, new BigDecimal(quantity), new BigDecimal(price), category);
    }

    static InvoiceLine lump(String text, String amount, TaxCategory category) {
        return InvoiceLine.lumpSum(text, new BigDecimal(amount), category);
    }

    static InvoiceDocument invoice(Party buyer, String buyerReference, InvoiceFormat format, InvoiceLine... lines) {
        return invoice(SELLER, buyer, buyerReference, format, lines);
    }

    static InvoiceDocument invoice(Party seller, Party buyer, String buyerReference, InvoiceFormat format,
            InvoiceLine... lines) {
        return new InvoiceDocument("2027-10", DAY, DAY.minusDays(2), DAY.minusDays(2), DAY.plusDays(14),
                seller, buyer, buyerReference, "DE02120300000000202051", "BYLADEM1001",
                "vielen Dank für Ihren Auftrag.", "Mit freundlichen Grüßen", null, format, List.of(lines));
    }

    public static InvoiceDocument reading(InvoiceFormat format) {
        return invoice(PUBLISHER, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                lump("Lesung aus „Der stille Hafen“ am 16.03.2027, 90 Min.", "400.00", TaxCategory.KLEINUNTERNEHMER),
                lump("Reisekosten", "80.00", TaxCategory.KLEINUNTERNEHMER));
    }

    /** Teaching under §4 Nr. 21 only: 24 × 41,00 €, total 984,00 €. */
    public static InvoiceDocument school(InvoiceFormat format) {
        return invoice(SCHOOL, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                line("Unterricht Deutsch B2, je 45 Min.", "24", "41.00", TaxCategory.EXEMPT_4_21));
    }

    /** Teaching and a reading: 16 × 45,00 € under §4 Nr. 21 and 400,00 € under §19, total 1 120,00 €. */
    public static InvoiceDocument mixed(InvoiceFormat format) {
        return invoice(SCHOOL, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                line("Unterricht Deutsch B2, je 45 Min.", "16", "45.00", TaxCategory.EXEMPT_4_21),
                lump("Lesung", "400.00", TaxCategory.KLEINUNTERNEHMER));
    }

    /** Two thirds count: 400,00 € under §19 and 200,00 € under §4 Nr. 21, total 600,00 €. */
    public static InvoiceDocument twoThirds(InvoiceFormat format) {
        return invoice(SCHOOL, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                lump("Lesung", "400.00", TaxCategory.KLEINUNTERNEHMER),
                lump("Unterricht", "200.00", TaxCategory.EXEMPT_4_21));
    }

    /** Half §19, half §4 Nr. 21: 10,00 € each, total 20,00 €. */
    public static InvoiceDocument halves(InvoiceFormat format) {
        return invoice(SCHOOL, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                lump("Lesung", "10.00", TaxCategory.KLEINUNTERNEHMER),
                lump("Unterricht", "10.00", TaxCategory.EXEMPT_4_21));
    }

    /** Teaching and books with VAT: 720,00 € under §4 Nr. 21 and 2 × 19,90 € at 19 %, total 767,36 €. */
    public static InvoiceDocument teachingAndBooks(InvoiceFormat format) {
        return invoice(SHOP, SCHOOL, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                line("Unterricht", "16", "45.00", TaxCategory.EXEMPT_4_21),
                line("Lehrbuch", "2", "19.90", TaxCategory.STANDARD_19));
    }

    /** Four categories: 34 € §4 Nr. 21, 34 € reverse charge, 31 € at 19 % and 1 € at 7 %, total 105,96 €. */
    public static InvoiceDocument fourWays(InvoiceFormat format) {
        return invoice(SELLER_EU, DUTCH, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                lump("Unterricht", "34.00", TaxCategory.EXEMPT_4_21),
                lump("Workshop", "34.00", TaxCategory.REVERSE_CHARGE_EU),
                lump("Lehrbuch", "31.00", TaxCategory.STANDARD_19),
                lump("Zeitschrift", "1.00", TaxCategory.STANDARD_7));
    }

    /** A free §19 line: total 0,00 €. */
    public static InvoiceDocument free(InvoiceFormat format) {
        return invoice(PUBLISHER, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                lump("Gratis", "0", TaxCategory.KLEINUNTERNEHMER));
    }

    /** The Stornorechnung 2027-10 of the reading, invoice 2027-07 of March 8, with the same lines. */
    public static InvoiceDocument cancellation(InvoiceFormat format) {
        InvoiceDocument d = reading(format);
        return new InvoiceDocument(d.number(), d.issueDate(), d.serviceFrom(), d.serviceTo(), d.dueDate(), d.seller(),
                d.buyer(), d.buyerReference(), d.iban(), d.bic(), null, d.closing(), d.taxNote(), d.format(), d.lines(),
                new InvoiceReference("2027-07", DAY.minusDays(10)));
    }

    /**
     * A sale of goods with both VAT rates: 3 × 24,90 € at 7 %, 10 × 4,99 € and 5,90 € shipping at 19 %.
     * Net 130,50 €, VAT 5,23 € and 10,60 €, total 146,33 €.
     */
    public static InvoiceDocument sale(InvoiceFormat format) {
        return invoice(SHOP, PUBLISHER, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                line("Fachbuch „E-Rechnung in der Praxis“", "3", "24.90", TaxCategory.STANDARD_7),
                line("Lesezeichen aus Leder", "10", "4.99", TaxCategory.STANDARD_19),
                lump("Versand", "5.90", TaxCategory.STANDARD_19));
    }

    static SpringTemplateEngine templates() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    static EInvoiceService service() {
        EInvoiceWriter writer = new EInvoiceWriter(new MustangInvoiceMapper());
        return new EInvoiceService(new InvoicePdfRenderer(templates()), writer, new ZugferdValidator(),
                new XRechnungValidator(java.nio.file.Path.of("target/kosit-xrechnung")));
    }
}
