package io.github.grigoriev.im.einvoice;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Produces an e-invoice and validates it for its format, see section 4 of {@code docs/PLAN.md}.
 *
 * <ul>
 *   <li>ZUGFeRD EN16931: Mustang validator.
 *   <li>ZUGFeRD XRECHNUNG: Mustang validator, plus KoSIT on the embedded XML.
 *   <li>XRechnung: KoSIT. The PDF is a viewing copy without XML.
 * </ul>
 */
@Service
public class EInvoiceService {

    private final InvoicePdfRenderer renderer;
    private final EInvoiceWriter writer;
    private final ZugferdValidator zugferdValidator;
    private final XRechnungValidator xrechnungValidator;

    /**
     * Creates the service.
     *
     * @param renderer renders the visible invoice
     * @param writer writes the e-invoice
     * @param zugferdValidator validates ZUGFeRD PDFs
     * @param xrechnungValidator validates XRechnung XML
     */
    public EInvoiceService(InvoicePdfRenderer renderer, EInvoiceWriter writer, ZugferdValidator zugferdValidator,
            XRechnungValidator xrechnungValidator) {
        this.renderer = renderer;
        this.writer = writer;
        this.zugferdValidator = zugferdValidator;
        this.xrechnungValidator = xrechnungValidator;
    }

    /**
     * Produces and validates the e-invoice.
     *
     * @param document the invoice
     * @return the files and the validation results
     * @throws EInvoiceException if a validation fails
     */
    public IssuedEInvoice issue(InvoiceDocument document) {
        byte[] pdfA = renderer.render(document);
        List<ValidationResult> results = new ArrayList<>();
        byte[] pdf;
        byte[] xml;
        if (document.format().isPdf()) {
            pdf = writer.zugferd(document, pdfA);
            xml = writer.embeddedXml(pdf);
            results.add(zugferdValidator.validate(pdf));
        } else {
            pdf = pdfA;
            xml = writer.xrechnung(document);
        }
        if (document.format().followsXRechnung()) {
            results.add(xrechnungValidator.validate(xml));
        }
        List<ValidationResult> failed = results.stream().filter(result -> !result.valid()).toList();
        if (!failed.isEmpty()) {
            throw new EInvoiceException("Invoice " + document.number() + " failed validation: " + describe(failed));
        }
        return new IssuedEInvoice(document.format(), pdf, xml, results);
    }

    private static String describe(List<ValidationResult> failed) {
        return failed.stream()
                .map(result -> result.validator() + " " + String.join("; ", result.messages()))
                .collect(Collectors.joining(" | "));
    }
}
