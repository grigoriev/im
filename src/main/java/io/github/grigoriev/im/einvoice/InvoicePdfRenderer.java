package io.github.grigoriev.im.einvoice;

import io.github.grigoriev.im.format.GermanFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        return render("pdf/invoice", Map.of("invoice", document, "fmt", new GermanFormat(), "address",
                AddressLayout.of(document.buyer()), "partnerLabelBreaks",
                InfoBox.breaksPartnerLabel(document.references().sellerIdentifier())));
    }

    /**
     * Renders a Thymeleaf template of the PDF layout as PDF/A-3B, with the fonts of the invoices.
     *
     * @param template the template, for example {@code pdf/procedure}
     * @param variables the values of the template
     * @return the PDF/A-3B bytes
     */
    public byte[] render(String template, Map<String, Object> variables) {
        String xhtml = templates.process(template, new Context(GERMAN, variables));
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
}
