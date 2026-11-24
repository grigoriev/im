package io.github.grigoriev.im.customer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.Party;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerTest {

    @Test
    void startsEmptyInGermanyWithZugferd() {
        Customer empty = Customer.empty();
        assertThat(empty.id()).isNull();
        assertThat(empty.countryCode()).isEqualTo("DE");
        assertThat(empty.format()).isEqualTo(InvoiceFormat.ZUGFERD_EN16931);
    }

    @Test
    void becomesTheBuyerParty() {
        Party party = CustomerTestData.vhs().toParty();
        assertThat(party.name()).isEqualTo("Volkshochschule Musterstadt");
        assertThat(party.street()).isEqualTo("Am Markt 1");
        assertThat(party.postcode()).isEqualTo("86150");
        assertThat(party.city()).isEqualTo("Musterstadt");
        assertThat(party.countryCode()).isEqualTo("DE");
        assertThat(party.email()).isEqualTo("erechnung@vhs.example");
        assertThat(party.contactName()).isEqualTo("Frau Beispiel");
        assertThat(party.addressAddition()).isNull();
        assertThat(party.phone()).isNull();
        assertThat(party.taxNumber()).isNull();
        assertThat(party.vatId()).isNull();
    }

    @Test
    void theBuyerPartyCarriesTheAddressAddition() {
        Party party = CustomerTestData.school().toParty();
        assertThat(party.addressAddition()).isEqualTo("Abrechnungen Online-Sprachkurse");
        assertThat(party.contactName()).isEqualTo("Frau Weber");
    }

    @Test
    void copiesWithAnotherId() {
        Customer copy = CustomerTestData.school().withId(7L);
        assertThat(copy.id()).isEqualTo(7L);
        assertThat(copy.withId(null)).isEqualTo(CustomerTestData.school());
    }

    @Test
    void theExceptionKeepsACopyOfTheProblems() {
        List<String> problems = new ArrayList<>(List.of("Eins.", "Zwei."));
        CustomerException exception = new CustomerException(problems);
        problems.clear();
        assertThat(exception.problems()).containsExactly("Eins.", "Zwei.");
        assertThat(exception.getMessage()).isEqualTo("Eins. Zwei.");
    }

    @Test
    void namesTheContactPersonInLists() {
        Customer verlag = CustomerTestData.verlag();
        assertThat(Customer.label("Sprachschule am See", "Frau Weber")).isEqualTo("Sprachschule am See · Frau Weber");
        assertThat(Customer.label("Sprachschule am See", null)).isEqualTo("Sprachschule am See");
        assertThat(Customer.label("Sprachschule am See", " ")).isEqualTo("Sprachschule am See");
        assertThat(verlag.label()).isEqualTo(Customer.label(verlag.name(), verlag.contactName()));
    }

    @Test
    void copiesTheOrganizationForAnotherContact() {
        Customer original = new Customer(7L, "Sprachschule am See", "KST 80441000, Bereich 44 DaF-Programme", "Seeweg 5",
                "86150", "Musterstadt", "DE", "Frau Weber", "weber@sprachschule.example", "DE123456789",
                "04011000-12345-67", "70001234", InvoiceFormat.XRECHNUNG, null);
        assertThat(original.forAnotherContact()).isEqualTo(new Customer(null, "Sprachschule am See",
                "KST 80441000, Bereich 44 DaF-Programme", "Seeweg 5", "86150", "Musterstadt", "DE", null, null,
                "DE123456789", "04011000-12345-67", "70001234", InvoiceFormat.XRECHNUNG, null));
    }
}
