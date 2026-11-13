package io.github.grigoriev.im.einvoice;

import java.util.Objects;

/**
 * Seller or buyer of an invoice.
 *
 * @param name legal name
 * @param street street and number
 * @param postcode postcode
 * @param city city
 * @param countryCode ISO 3166-1 alpha-2 country code
 * @param email e-mail address, may be {@code null}
 * @param phone phone number, may be {@code null}
 * @param contactName contact person, may be {@code null}
 * @param taxNumber German tax number (Steuernummer), may be {@code null}
 * @param vatId VAT identification number, may be {@code null}
 */
public record Party(
        String name,
        String street,
        String postcode,
        String city,
        String countryCode,
        String email,
        String phone,
        String contactName,
        String taxNumber,
        String vatId) {

    /** Checks the mandatory address fields. */
    public Party {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(postcode, "postcode");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(countryCode, "countryCode");
    }
}
