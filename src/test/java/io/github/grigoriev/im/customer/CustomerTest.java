package io.github.grigoriev.im.customer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.Party;
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
        assertThat(party.phone()).isNull();
        assertThat(party.taxNumber()).isNull();
        assertThat(party.vatId()).isNull();
    }

    @Test
    void copiesWithAnotherId() {
        Customer copy = CustomerTestData.verlag().withId(7L);
        assertThat(copy.id()).isEqualTo(7L);
        assertThat(copy.withId(null)).isEqualTo(CustomerTestData.verlag());
    }

    @Test
    void theExceptionKeepsACopyOfTheProblems() {
        List<String> problems = new java.util.ArrayList<>(List.of("Eins.", "Zwei."));
        CustomerException exception = new CustomerException(problems);
        problems.clear();
        assertThat(exception.problems()).containsExactly("Eins.", "Zwei.");
        assertThat(exception.getMessage()).isEqualTo("Eins. Zwei.");
    }

    @Test
    void namesTheContactPersonInLists() {
        Customer verlag = CustomerTestData.verlag();
        assertThat(Customer.label("Goethe-Institut", "Frau Weber")).isEqualTo("Goethe-Institut · Frau Weber");
        assertThat(Customer.label("Goethe-Institut", null)).isEqualTo("Goethe-Institut");
        assertThat(Customer.label("Goethe-Institut", " ")).isEqualTo("Goethe-Institut");
        assertThat(verlag.label()).isEqualTo(Customer.label(verlag.name(), verlag.contactName()));
    }

    @Test
    void copiesTheOrganizationForAnotherContact() {
        Customer original = new Customer(7L, "Goethe-Institut", "Dachauer Str. 122", "80637", "München", "DE",
                "Frau Weber", "weber@goethe.example", "DE123456789", "04011000-12345-67", InvoiceFormat.XRECHNUNG);
        assertThat(original.forAnotherContact()).isEqualTo(new Customer(null, "Goethe-Institut", "Dachauer Str. 122",
                "80637", "München", "DE", null, null, "DE123456789", "04011000-12345-67", InvoiceFormat.XRECHNUNG));
    }
}
