package io.github.grigoriev.im.einvoice;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The files of an issued e-invoice and the results of their validation.
 *
 * @param format the format
 * @param pdf the PDF: the e-invoice for ZUGFeRD, a viewing copy for XRechnung
 * @param xml the e-invoice XML, embedded in the PDF for ZUGFeRD
 * @param validations results of every validation that ran
 */
public record IssuedEInvoice(InvoiceFormat format, byte[] pdf, byte[] xml, List<ValidationResult> validations) {

    /** Copies the validation results. */
    public IssuedEInvoice {
        validations = List.copyOf(validations);
    }

    /** Compares the content of the files, not the array references. */
    @Override
    public boolean equals(Object other) {
        return other instanceof IssuedEInvoice that
                && format == that.format
                && Arrays.equals(pdf, that.pdf)
                && Arrays.equals(xml, that.xml)
                && validations.equals(that.validations);
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
