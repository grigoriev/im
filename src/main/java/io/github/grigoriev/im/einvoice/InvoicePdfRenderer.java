package io.github.grigoriev.im.einvoice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.BaseFont;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.PdfAConformance;

/** Renders the visible invoice as PDF/A-3B. The XML is embedded later by {@link EInvoiceWriter}. */
@Component
public class InvoicePdfRenderer {

    static final List<String> DEFAULT_FONTS = List.of("fonts/JetBrainsMonoNL-Regular.ttf", "fonts/JetBrainsMonoNL-SemiBold.ttf");

    private static final Locale GERMAN = Locale.GERMANY;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", GERMAN);

    private final ITemplateEngine templates;
    private final List<String> fonts;

    /**
     * Creates the renderer.
     *
     * @param templates the Thymeleaf engine that holds {@code pdf/invoice.html}
     */
    @Autowired
    public InvoicePdfRenderer(ITemplateEngine templates) {
        this(templates, DEFAULT_FONTS);
    }

    InvoicePdfRenderer(ITemplateEngine templates, List<String> fonts) {
        this.templates = templates;
        this.fonts = List.copyOf(fonts);
    }

    /**
     * Renders the invoice.
     *
     * @param document the invoice
     * @return the PDF/A-3B bytes
     */
    public byte[] render(InvoiceDocument document) {
        Context context = new Context(GERMAN, Map.of("invoice", document, "fmt", new Formatter()));
        String xhtml = templates.process("pdf/invoice", context);
        ITextRenderer renderer = new ITextRenderer();
        renderer.setPdfAConformance(PdfAConformance.PDF_A_3B);
        for (String font : fonts) {
            addFont(renderer, font);
        }
        renderer.setDocumentFromString(xhtml);
        renderer.layout();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.createPDF(out);
        return out.toByteArray();
    }

    private static void addFont(ITextRenderer renderer, String resource) {
        var url = InvoicePdfRenderer.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new EInvoiceException("Font not found: " + resource);
        }
        try {
            renderer.getFontResolver().addFont(url.toExternalForm(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (IOException | DocumentException e) {
            throw new EInvoiceException("Cannot load font " + resource, e);
        }
    }

    /** German number and date formats for the template. */
    public static final class Formatter {

        /**
         * Formats an amount, for example {@code 1.234,50 €}.
         *
         * @param amount the amount
         * @return the formatted amount
         */
        public String money(BigDecimal amount) {
            NumberFormat format = NumberFormat.getCurrencyInstance(GERMAN);
            format.setRoundingMode(RoundingMode.HALF_UP);
            return format.format(amount);
        }

        /**
         * Formats a unit price with two to four decimals, for example {@code 0,005 €}.
         *
         * @param price the unit price
         * @return the formatted price
         */
        public String price(BigDecimal price) {
            NumberFormat format = NumberFormat.getCurrencyInstance(GERMAN);
            format.setMaximumFractionDigits(InvoiceLine.MAX_DECIMALS);
            format.setRoundingMode(RoundingMode.HALF_UP);
            return format.format(price);
        }

        /**
         * Formats a VAT rate, for example {@code 19 %} or {@code 7 %}.
         *
         * @param rate the rate in percent
         * @return the formatted rate
         */
        public String percent(BigDecimal rate) {
            return quantity(rate) + "\u00a0%";
        }

        /**
         * Formats a quantity without trailing zeros, for example {@code 1,5}.
         *
         * @param quantity the quantity
         * @return the formatted quantity
         */
        public String quantity(BigDecimal quantity) {
            NumberFormat format = NumberFormat.getNumberInstance(GERMAN);
            format.setMaximumFractionDigits(4);
            format.setRoundingMode(RoundingMode.HALF_UP);
            return format.format(quantity);
        }

        /**
         * Formats a date, for example {@code 18.03.2027}.
         *
         * @param date the date
         * @return the formatted date
         */
        public String date(LocalDate date) {
            return DATE.format(date);
        }

        /**
         * Groups an IBAN in blocks of four, for example {@code DE02 1203 0000 ...}.
         *
         * @param iban the IBAN without spaces
         * @return the grouped IBAN
         */
        public String iban(String iban) {
            return iban.replaceAll("(.{4})(?!$)", "$1 ");
        }

        /**
         * Formats a service period. A period of one day shows that day only.
         *
         * @param from first day
         * @param to last day
         * @return the formatted period
         */
        public String period(LocalDate from, LocalDate to) {
            return from.equals(to) ? date(from) : date(from) + " bis " + date(to);
        }
    }
}
