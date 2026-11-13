package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Fictitious invoices for the tests. No real person or company. */
final class SampleInvoices {

    static final Party SELLER = new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE",
            "kontakt@sommer.example", "+49 89 1234567", "Elena Sommer", "143/123/45678", null);
    /** The seller with a VAT ID, as reverse charge needs it (§14a UStG). */
    static final Party SELLER_EU = new Party("Elena Sommer", "Lindenstraße 12", "80331", "München", "DE",
            "kontakt@sommer.example", "+49 89 1234567", "Elena Sommer", "143/123/45678", "DE123456789");
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

    static InvoiceDocument reading(InvoiceFormat format) {
        return invoice(PUBLISHER, format.followsXRechnung() ? "04011000-12345-67" : null, format,
                lump("Lesung aus „Der stille Hafen“ am 16.03.2027, 90 Min.", "400.00", TaxCategory.KLEINUNTERNEHMER),
                lump("Reisekosten", "80.00", TaxCategory.KLEINUNTERNEHMER));
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
