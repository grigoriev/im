package io.github.grigoriev.im.customer;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.Party;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A customer: the buyer of the invoices.
 *
 * @param id database ID, {@code null} before the first save
 * @param name legal name
 * @param street street and number
 * @param postcode postcode
 * @param city city
 * @param countryCode ISO 3166-1 alpha-2 country code
 * @param contactName contact person, may be {@code null}
 * @param email address for invoices, may be {@code null}; both XRechnung formats need it
 * @param vatId VAT identification number, may be {@code null}; reverse charge needs it
 * @param buyerReference buyer reference, for a public buyer the Leitweg-ID; both XRechnung formats need it
 * @param format the e-invoice format of new invoices
 */
@Table("customer")
public record Customer(
        @Id Long id,
        String name,
        String street,
        String postcode,
        String city,
        String countryCode,
        String contactName,
        String email,
        String vatId,
        String buyerReference,
        InvoiceFormat format) {

    /**
     * Returns an empty customer for the form: Germany, ZUGFeRD EN16931.
     *
     * @return the empty customer
     */
    public static Customer empty() {
        return new Customer(null, "", "", "", "", "DE", null, null, null, null, InvoiceFormat.ZUGFERD_EN16931);
    }

    /**
     * Returns a copy with another ID.
     *
     * @param newId the ID, {@code null} for a new customer
     * @return the copy
     */
    public Customer withId(Long newId) {
        return new Customer(newId, name, street, postcode, city, countryCode, contactName, email, vatId, buyerReference,
                format);
    }

    /**
     * Returns the name in lists: the organization and, if set, the contact person, so two contacts of one
     * organization stay apart.
     *
     * @return for example "Goethe-Institut · Frau Weber"
     */
    public String label() {
        return label(name, contactName);
    }

    /**
     * Returns the name of an organization with its contact person, as {@link #label()} does.
     *
     * @param name the name
     * @param contactName the contact person, may be {@code null} or blank
     * @return the label
     */
    public static String label(String name, String contactName) {
        return contactName == null || contactName.isBlank() ? name : name + " · " + contactName;
    }

    /**
     * Returns a new customer for another contact person of the same organization: the address, VAT ID, buyer
     * reference and format stay, the contact person and e-mail are empty.
     *
     * @return the copy, without an ID
     */
    public Customer forAnotherContact() {
        return new Customer(null, name, street, postcode, city, countryCode, null, null, vatId, buyerReference, format);
    }

    /**
     * Returns the buyer party of an invoice.
     *
     * @return the party
     */
    public Party toParty() {
        return new Party(name, street, postcode, city, countryCode, email, null, contactName, null, vatId);
    }
}
