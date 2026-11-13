package io.github.grigoriev.im.einvoice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.mustangproject.ZUGFeRD.IZUGFeRDExporter;
import org.mustangproject.ZUGFeRD.Profile;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;
import org.springframework.stereotype.Component;

/** Writes the e-invoice: XML embedded into the PDF/A-3 for ZUGFeRD, plain XML for XRechnung. */
@Component
public class EInvoiceWriter {

    private static final String PRODUCER = "im";

    private final MustangInvoiceMapper mapper;

    /**
     * Creates the writer.
     *
     * @param mapper maps the invoice to the Mustang model
     */
    public EInvoiceWriter(MustangInvoiceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Embeds the invoice XML into a PDF/A-3B and returns the ZUGFeRD PDF.
     *
     * @param document the invoice, in one of the ZUGFeRD formats
     * @param pdfA the visible invoice as PDF/A-3B
     * @return the ZUGFeRD PDF
     */
    public byte[] zugferd(InvoiceDocument document, byte[] pdfA) {
        if (!document.format().isPdf()) {
            throw new IllegalArgumentException("Format " + document.format() + " has no PDF");
        }
        try (IZUGFeRDExporter exporter = new ZUGFeRDExporterFromPDFA().load(pdfA)) {
            exporter.setProducer(PRODUCER)
                    .setCreator(document.seller().name())
                    .setProfile(profile(document.format()));
            exporter.setTransaction(mapper.toMustang(document));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.export(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new EInvoiceException("Cannot write the ZUGFeRD PDF of invoice " + document.number(), e);
        }
    }

    /**
     * Returns the invoice as XRechnung CII XML.
     *
     * @param document the invoice
     * @return the XML bytes
     */
    public byte[] xrechnung(InvoiceDocument document) {
        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(profile(InvoiceFormat.XRECHNUNG));
        provider.generateXML(mapper.toMustang(document));
        return provider.getXML();
    }

    /**
     * Reads the XML embedded in a ZUGFeRD PDF.
     *
     * @param pdf the ZUGFeRD PDF
     * @return the embedded XML
     */
    public byte[] embeddedXml(byte[] pdf) {
        byte[] xml = new ZUGFeRDImporter(new ByteArrayInputStream(pdf)).getRawXML();
        if (xml == null) {
            throw new EInvoiceException("The PDF carries no e-invoice XML");
        }
        return xml;
    }

    static Profile profile(InvoiceFormat format) {
        return Profiles.getByName(format.followsXRechnung() ? "XRECHNUNG" : "EN16931");
    }
}
