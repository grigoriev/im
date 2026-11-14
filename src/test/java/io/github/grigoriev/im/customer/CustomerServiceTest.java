package io.github.grigoriev.im.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CustomerServiceTest {

    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final CustomerService service = new CustomerService(repository, audit);

    private static Customer with(String field, String value) {
        Customer c = CustomerTestData.verlag();
        return new Customer(c.id(),
                field.equals("name") ? value : c.name(),
                field.equals("street") ? value : c.street(),
                field.equals("postcode") ? value : c.postcode(),
                field.equals("city") ? value : c.city(),
                field.equals("country") ? value : c.countryCode(),
                field.equals("contact") ? value : c.contactName(),
                field.equals("email") ? value : c.email(),
                field.equals("vatId") ? value : c.vatId(),
                field.equals("reference") ? value : c.buyerReference(),
                c.format());
    }

    @Test
    void listsAndFindsCustomers() {
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(CustomerTestData.verlag()));
        assertThat(service.list()).containsExactly(CustomerTestData.verlag());
        when(repository.findById(3L)).thenReturn(Optional.of(CustomerTestData.verlag().withId(3L)));
        assertThat(service.find(3)).contains(CustomerTestData.verlag().withId(3L));
    }

    @Test
    void createsANormalizedCustomerWithoutTheEnteredId() {
        Customer c = CustomerTestData.verlag();
        Customer input = new Customer(99L, "  Verlag am Fluss GmbH ", c.street(), c.postcode(), c.city(), " de ", " ",
                " buchhaltung@verlag.example ", " nl 123456789 b01 ", "", InvoiceFormat.ZUGFERD_EN16931);
        when(repository.save(any())).thenAnswer(call -> ((Customer) call.getArgument(0)).withId(5L));
        Customer saved = service.create(input);
        assertThat(saved.id()).isEqualTo(5L);
        verify(repository).save(new Customer(null, "Verlag am Fluss GmbH", c.street(), c.postcode(), c.city(), "DE",
                null, "buchhaltung@verlag.example", "NL123456789B01", null, InvoiceFormat.ZUGFERD_EN16931));
        verify(audit).add(AuditAction.CUSTOMER_CREATED, "Verlag am Fluss GmbH");
    }

    @Test
    void updatesAnExistingCustomerOnly() {
        when(repository.existsById(4L)).thenReturn(true);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.update(4, CustomerTestData.verlag().withId(99L)).id()).isEqualTo(4L);
        Customer verlag = CustomerTestData.verlag();
        assertThatThrownBy(() -> service.update(5, verlag)).isInstanceOf(CustomerException.class)
                .hasMessage("Diesen Kunden gibt es nicht.");
        verify(repository, never()).save(CustomerTestData.verlag().withId(5L));
        verify(audit).add(AuditAction.CUSTOMER_CHANGED, "Verlag am Fluss GmbH");
    }

    @Test
    void deletesAnExistingCustomerOnly() {
        when(repository.findById(4L)).thenReturn(Optional.of(CustomerTestData.verlag().withId(4L)));
        assertThat(service.delete(4).name()).isEqualTo("Verlag am Fluss GmbH");
        verify(repository).deleteById(4L);
        assertThatThrownBy(() -> service.delete(5)).isInstanceOf(CustomerException.class)
                .hasMessage("Diesen Kunden gibt es nicht.");
        verify(repository, never()).deleteById(5L);
        verify(audit).add(AuditAction.CUSTOMER_DELETED, "Verlag am Fluss GmbH");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "name      | ''                 | Bitte Name angeben.",
        "street    |                    | Bitte Straße angeben.",
        "postcode  | ' '                | Bitte PLZ angeben.",
        "city      | ''                 | Bitte Ort angeben.",
        "country   | DEU                | Bitte ein Land aus der Liste wählen.",
        "email     | verlag.example     | Bitte eine gültige E-Mail-Adresse angeben.",
        "vatId     | 123456789          | Die USt-IdNr. beginnt mit dem Ländercode, zum Beispiel DE123456789."
    })
    void refusesAWrongField(String field, String value, String problem) {
        Customer wrong = with(field, value);
        CustomerException e = catchThrowableOfType(CustomerException.class, () -> service.create(wrong));
        assertThat(e.problems()).containsExactly(problem);
        verify(repository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @CsvSource({"name, 200, Name", "street, 200, Straße", "postcode, 16, PLZ", "city, 100, Ort",
        "contact, 200, Ansprechpartner", "reference, 200, Käuferreferenz"})
    void limitsTheLength(String field, int max, String label) {
        assertThat(CustomerService.check(CustomerService.normalize(with(field, "x".repeat(max))))).isEmpty();
        assertThat(CustomerService.check(CustomerService.normalize(with(field, "x".repeat(max + 1)))))
                .containsExactly(label + " hat höchstens " + max + " Zeichen.");
    }

    @Test
    void acceptsACustomerWithoutEmail() {
        assertThat(CustomerService.check(with("email", null))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"XRECHNUNG", "ZUGFERD_XRECHNUNG"})
    void xrechnungNeedsABuyerReferenceAndAnEmail(InvoiceFormat format) {
        Customer v = CustomerTestData.vhs();
        assertThat(CustomerService.check(new Customer(null, v.name(), v.street(), v.postcode(), v.city(), v.countryCode(),
                null, v.email(), null, v.buyerReference(), format))).isEmpty();
        assertThat(CustomerService.check(new Customer(null, v.name(), v.street(), v.postcode(), v.city(), v.countryCode(),
                null, null, null, null, format))).containsExactly(
                "Die XRechnung braucht eine Käuferreferenz, zum Beispiel die Leitweg-ID (BR-DE-15).",
                "Die XRechnung braucht die E-Mail-Adresse des Kunden.");
    }

    @Test
    void needsAFormat() {
        Customer v = CustomerTestData.verlag();
        assertThat(CustomerService.check(new Customer(null, v.name(), v.street(), v.postcode(), v.city(), v.countryCode(),
                null, null, null, null, null))).containsExactly("Bitte das E-Rechnungsformat wählen.");
    }
}
