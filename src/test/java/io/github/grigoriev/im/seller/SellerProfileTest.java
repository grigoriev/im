package io.github.grigoriev.im.seller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.einvoice.Party;
import org.junit.jupiter.api.Test;

class SellerProfileTest {

    @Test
    void startsEmptyInGermanyAsKleinunternehmer() {
        SellerProfile empty = SellerProfile.empty();
        assertThat(empty.name()).isEmpty();
        assertThat(empty.countryCode()).isEqualTo("DE");
        assertThat(empty.taxStatus()).isEqualTo(TaxStatus.KLEINUNTERNEHMER);
        assertThat(empty.ibanGrouped()).isEmpty();
    }

    @Test
    void becomesTheSellerPartyWithTheNameAsContact() {
        Party party = SellerTestData.profile().toParty();
        assertThat(party.name()).isEqualTo("Erika Mustermann");
        assertThat(party.contactName()).isEqualTo("Erika Mustermann");
        assertThat(party.email()).isEqualTo("erika@example.org");
        assertThat(party.phone()).isEqualTo("+49 30 1234567");
        assertThat(party.taxNumber()).isEqualTo("12/345/67890");
        assertThat(party.vatId()).isNull();
        assertThat(party.countryCode()).isEqualTo("DE");
        assertThat(party.street()).isEqualTo("Musterweg 1");
        assertThat(party.postcode()).isEqualTo("12345");
        assertThat(party.city()).isEqualTo("Musterstadt");
    }

    @Test
    void keepsAnExplicitContact() {
        SellerProfile p = SellerTestData.profile();
        SellerProfile withContact = new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), p.countryCode(),
                "Max Mustermann", p.email(), p.phone(), p.taxNumber(), p.vatId(), p.iban(), p.bic(), p.taxStatus(), null, null, null, null);
        assertThat(withContact.toParty().contactName()).isEqualTo("Max Mustermann");
    }

    @Test
    void theNameHoldsTheAccountUnlessAnotherHolderIsSet() {
        SellerProfile p = SellerTestData.profile();
        assertThat(p.holder()).isEqualTo("Erika Mustermann");
        SellerProfile other = new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), p.countryCode(), null,
                p.email(), p.phone(), p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null, null, null,
                "Mustermann Sprachkurse GbR");
        assertThat(other.holder()).isEqualTo("Mustermann Sprachkurse GbR");
        assertThat(SellerProfile.empty().tagline()).isNull();
    }

    @Test
    void groupsTheIbanInFours() {
        assertThat(SellerTestData.profile().ibanGrouped()).isEqualTo("DE89 3704 0044 0532 0130 00");
        SellerProfile p = SellerTestData.profile();
        assertThat(new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), p.countryCode(), null, p.email(),
                p.phone(), p.taxNumber(), p.vatId(), null, p.bic(), p.taxStatus(), null, null, null, null).ibanGrouped()).isEmpty();
    }
}
