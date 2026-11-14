package io.github.grigoriev.im.seller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** A tab of the profile. Each tab saves its own fields and keeps the others as stored. */
public enum ProfilePart {

    /** Name and address. */
    ADDRESS("anschrift", "Anschrift", List.of("name", "tagline", "street", "postcode", "city", "countryCode")),
    /** Contact person, e-mail, phone and website. */
    CONTACT("kontakt", "Kontakt", List.of("contactName", "email", "phone", "website")),
    /** Tax status and tax numbers. */
    TAX("steuer", "Steuer", List.of("taxStatus", "taxNumber", "vatId")),
    /** Bank account. */
    BANK("bank", "Bankverbindung", List.of("iban", "bic", "bankName", "accountHolder"));

    private final String slug;
    private final String label;
    private final List<String> fields;

    ProfilePart(String slug, String label, List<String> fields) {
        this.slug = slug;
        this.label = label;
        this.fields = fields;
    }

    /**
     * Returns the part of a URL slug.
     *
     * @param slug the slug, for example {@code kontakt}
     * @return the part, empty for an unknown slug
     */
    public static Optional<ProfilePart> of(String slug) {
        return Arrays.stream(values()).filter(part -> part.slug.equals(slug)).findFirst();
    }

    /**
     * Returns the slug in the URL.
     *
     * @return the slug
     */
    public String slug() {
        return slug;
    }

    /**
     * Returns the German name of the tab.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the names of the fields of this part, as in {@link SellerProfile}.
     *
     * @return the field names
     */
    public List<String> fields() {
        return fields;
    }

    /**
     * Takes the fields of this part from the input and the others from the stored profile.
     *
     * @param stored the stored profile
     * @param input the submitted form, whose other fields are empty
     * @return the profile to save
     */
    public SellerProfile merge(SellerProfile stored, SellerProfile input) {
        SellerProfile a = this == ADDRESS ? input : stored;
        SellerProfile c = this == CONTACT ? input : stored;
        SellerProfile t = this == TAX ? input : stored;
        SellerProfile b = this == BANK ? input : stored;
        return new SellerProfile(a.name(), a.street(), a.postcode(), a.city(), a.countryCode(), c.contactName(),
                c.email(), c.phone(), t.taxNumber(), t.vatId(), b.iban(), b.bic(), t.taxStatus(), a.tagline(),
                c.website(), b.bankName(), b.accountHolder());
    }
}
