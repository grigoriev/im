package io.github.grigoriev.im.einvoice;

import java.util.List;

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
}
