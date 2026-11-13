package io.github.grigoriev.im.einvoice;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The files of an issued e-invoice and the results of their validation. The files never change: the
 * record copies them on the way in and on the way out.
 *
 * @param format the format
 * @param pdf the PDF: the e-invoice for ZUGFeRD, a viewing copy for XRechnung
 * @param xml the e-invoice XML, embedded in the PDF for ZUGFeRD
 * @param validations results of every validation that ran
 */
public record IssuedEInvoice(InvoiceFormat format, byte[] pdf, byte[] xml, List<ValidationResult> validations) {

    /** Copies the files and the validation results. */
    public IssuedEInvoice {
        pdf = pdf.clone();
        xml = xml.clone();
        validations = List.copyOf(validations);
    }

    /**
     * Returns a copy of the PDF.
     *
     * @return the PDF bytes
     */
    @Override
    public byte[] pdf() {
        return pdf.clone();
    }

    /**
     * Returns a copy of the XML.
     *
     * @return the XML bytes
     */
    @Override
    public byte[] xml() {
        return xml.clone();
    }

    /** Compares the content of the files, not the array references. */
    @Override
    public boolean equals(Object other) {
        return other instanceof IssuedEInvoice(var otherFormat, var otherPdf, var otherXml, var otherValidations)
                && format == otherFormat
                && Arrays.equals(pdf, otherPdf)
                && Arrays.equals(xml, otherXml)
                && validations.equals(otherValidations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, Arrays.hashCode(pdf), Arrays.hashCode(xml), validations);
    }

    /** Names the file sizes instead of the bytes. */
    @Override
    public String toString() {
        return "IssuedEInvoice[format=" + format + ", pdf=" + pdf.length + " bytes, xml=" + xml.length
                + " bytes, validations=" + validations + "]";
    }
}
