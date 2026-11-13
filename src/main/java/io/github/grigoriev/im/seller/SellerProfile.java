package io.github.grigoriev.im.seller;

import io.github.grigoriev.im.einvoice.Party;

/**
 * The seller as every invoice shows it: address, contact, tax numbers and bank account.
 *
 * @param name legal name, for example the full name of a freelancer
 * @param street street and number
 * @param postcode postcode
 * @param city city
 * @param countryCode ISO 3166-1 alpha-2 country code
 * @param contactName contact person, {@code null} means the name
 * @param email e-mail address
 * @param phone phone number
 * @param taxNumber German tax number (Steuernummer), may be {@code null} if there is a VAT ID
 * @param vatId VAT identification number, may be {@code null} if there is a tax number
 * @param iban IBAN without spaces
 * @param bic BIC
 * @param taxStatus how the seller charges VAT
 */
public record SellerProfile(
        String name,
        String street,
        String postcode,
        String city,
        String countryCode,
        String contactName,
        String email,
        String phone,
        String taxNumber,
        String vatId,
        String iban,
        String bic,
        TaxStatus taxStatus) {

    /**
     * Returns an empty profile for the first form: Germany, Kleinunternehmer.
     *
     * @return the empty profile
     */
    public static SellerProfile empty() {
        return new SellerProfile("", "", "", "", "DE", null, "", "", null, null, "", "", TaxStatus.KLEINUNTERNEHMER);
    }

    /**
     * Returns the seller party of an invoice. XRechnung needs a contact name (BR-DE-5): the name serves if no
     * contact person is set.
     *
     * @return the party
     */
    public Party toParty() {
        return new Party(name, street, postcode, city, countryCode, email, phone,
                contactName == null ? name : contactName, taxNumber, vatId);
    }

    /**
     * Returns the IBAN in groups of four, as people read it.
     *
     * @return the formatted IBAN, empty if there is none
     */
    public String ibanGrouped() {
        return iban == null ? "" : iban.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
