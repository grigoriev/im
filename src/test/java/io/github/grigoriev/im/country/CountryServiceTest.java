package io.github.grigoriev.im.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class CountryServiceTest {

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec statement = mock(StatementSpec.class);
    @SuppressWarnings("unchecked")
    private final MappedQuerySpec<String> codes = mock(MappedQuerySpec.class);
    private final AuditService audit = mock(AuditService.class);
    private final CountryService service = new CountryService(jdbc, audit);

    @BeforeEach
    void statement() {
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.query(String.class)).thenReturn(codes);
        when(codes.list()).thenReturn(List.of("DE", "AT", "CH"));
    }

    @ParameterizedTest
    @CsvSource({"DE, true, Deutschland", "AT, true, Österreich", "CH, true, Schweiz", "XK, false, XK", "de, false, de"})
    void knowsTheIsoCountriesWithTheirGermanNames(String code, boolean known, String name) {
        assertThat(CountryService.isKnown(code)).isEqualTo(known);
        assertThat(CountryService.name(code)).isEqualTo(name);
    }

    @Test
    void doesNotKnowAMissingCode() {
        assertThat(CountryService.isKnown(null)).isFalse();
        assertThat(CountryService.name(null)).isEqualTo("null");
    }

    @Test
    void sortsTheShortListByGermanName() {
        assertThat(service.enabled()).containsExactly(new Country("DE", "Deutschland"), new Country("AT", "Österreich"),
                new Country("CH", "Schweiz"));
        verify(jdbc).sql(CountryService.LIST);
    }

    @Test
    void offersTheStoredCountryEvenIfTheAdminRemovedIt() {
        assertThat(service.options("JP")).extracting(Country::code).containsExactly("DE", "JP", "AT", "CH");
        assertThat(service.options("DE")).hasSize(3);
        assertThat(service.options((String) null)).hasSize(3);
        assertThat(service.options(" ")).hasSize(3);
    }

    @Test
    void keepsTheStoredAndTheSubmittedCountryOnce() {
        assertThat(service.options("JP", "FR")).extracting(Country::code).containsExactly("DE", "FR", "JP", "AT", "CH");
        assertThat(service.options("JP", "JP")).extracting(Country::code).containsExactly("DE", "JP", "AT", "CH");
        assertThat(service.options(null, "DE")).hasSize(3);
        assertThat(service.options()).hasSize(3);
    }

    @Test
    void showsAStoredCodeThatIsNoIsoCountryAsItIs() {
        assertThat(service.options("XK")).contains(new Country("XK", "XK")).hasSize(4);
    }

    @Test
    void offersEveryOtherCountryToAdd() {
        List<Country> others = service.others();
        assertThat(others).hasSize(CountryService.CODES.size() - 3);
        assertThat(others).extracting(Country::code).doesNotContain("DE", "AT", "CH").contains("JP", "US");
        assertThat(others.getFirst().name()).isEqualTo("Afghanistan");
    }

    @Test
    void addsAKnownCountryOnly() {
        when(statement.update()).thenReturn(1);
        assertThat(service.add("JP")).isEqualTo(new Country("JP", "Japan"));
        verify(jdbc).sql(CountryService.ADD);
        verify(statement).param("code", "JP");
        verify(statement).update();
        assertThatThrownBy(() -> service.add("ZZ")).isInstanceOf(IllegalArgumentException.class);
        verify(statement, never()).param("code", "ZZ");
        verify(audit).add(AuditAction.COUNTRY_ADDED, "JP");
        verify(audit, never()).add(AuditAction.COUNTRY_ADDED, "ZZ");
    }

    @Test
    void removesACountry() {
        when(statement.update()).thenReturn(1);
        assertThat(service.remove("CH")).isEqualTo(new Country("CH", "Schweiz"));
        verify(jdbc).sql(CountryService.REMOVE);
        verify(statement).param("code", "CH");
        verify(statement).update();
        verify(audit).add(AuditAction.COUNTRY_REMOVED, "CH");
    }

    @Test
    void recordsNothingWhenTheListStaysTheSame() {
        when(statement.update()).thenReturn(0);
        service.add("DE");
        service.remove("JP");
        org.mockito.Mockito.verifyNoInteractions(audit);
    }
}
