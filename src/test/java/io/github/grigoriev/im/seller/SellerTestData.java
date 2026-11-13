package io.github.grigoriev.im.seller;

/** Seller profiles for tests. The IBAN is the public example of the ISO standard, not a real account. */
public final class SellerTestData {

    /** A valid IBAN without spaces. */
    public static final String IBAN = "DE89370400440532013000";

    private SellerTestData() {
    }

    /**
     * Returns a valid Kleinunternehmer profile.
     *
     * @return the profile
     */
    public static SellerProfile profile() {
        return new SellerProfile("Erika Mustermann", "Musterweg 1", "12345", "Musterstadt", "DE", null,
                "erika@example.org", "+49 30 1234567", "12/345/67890", null, IBAN, "COBADEFFXXX",
                TaxStatus.KLEINUNTERNEHMER, "Freiberufliche Dozentin", "www.erika.example", "Commerzbank", null);
    }
}
