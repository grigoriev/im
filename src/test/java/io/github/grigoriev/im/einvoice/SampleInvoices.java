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
