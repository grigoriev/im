package io.github.grigoriev.im.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.seller.SettingsException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class NumberServiceTest {

    private static final Clock NEW_YEARS_EVE = Clock.fixed(Instant.parse("2027-12-31T23:30:00Z"), ZoneId.of("Europe/Berlin"));

    private final NumberSequenceRepository sequences = mock(NumberSequenceRepository.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec statement = mock(StatementSpec.class);
    @SuppressWarnings("unchecked")
    private final MappedQuerySpec<String> text = mock(MappedQuerySpec.class);
    @SuppressWarnings("unchecked")
    private final MappedQuerySpec<Integer> counter = mock(MappedQuerySpec.class);
    private final NumberService service = new NumberService(sequences, jdbc, NEW_YEARS_EVE);

    @BeforeEach
    void statement() {
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.query(String.class)).thenReturn(text);
        when(statement.query(Integer.class)).thenReturn(counter);
        when(text.optional()).thenReturn(Optional.empty());
    }

    @Test
    void usesTheDefaultPatternUntilTheAdminSetsOne() {
        assertThat(service.pattern()).isEqualTo(NumberPattern.DEFAULT);
        verify(jdbc).sql(NumberService.READ_PATTERN);
        verify(statement).param("key", NumberService.PATTERN_KEY);
        when(text.optional()).thenReturn(Optional.of("RE-{YY}-{NNN}"));
        assertThat(service.pattern().value()).isEqualTo("RE-{YY}-{NNN}");
    }

    @Test
    void savesACheckedPattern() {
        assertThat(service.savePattern(" RE-{YYYY}-{NNN} ").value()).isEqualTo("RE-{YYYY}-{NNN}");
        verify(jdbc).sql(NumberService.WRITE_PATTERN);
        verify(statement).param("value", "RE-{YYYY}-{NNN}");
        verify(statement).update();
        assertThatThrownBy(() -> service.savePattern("{NN}")).isInstanceOf(SettingsException.class);
    }

    @Test
    void showsTheCurrentAndTheNextYearInGermanTime() {
        when(sequences.findById(2028)).thenReturn(Optional.of(new NumberSequence(2028, 5, true)));
        when(sequences.findById(2029)).thenReturn(Optional.empty());
        assertThat(service.years()).containsExactly(new NumberSequence(2028, 5, true), new NumberSequence(2029, 1, false));
        assertThat(service.preview(new NumberSequence(2028, 5, true))).isEqualTo("2028-05");
    }

    @Test
    void setsTheStartOfAnUnusedYear() {
        when(statement.update()).thenReturn(1);
        service.setStart(2028, 10);
        verify(jdbc).sql(NumberService.CREATE_YEAR);
        verify(jdbc).sql(NumberService.START);
        verify(statement).param("start", 10);
        verify(statement, times(2)).param("year", 2028);
    }

    @Test
    void refusesTheStartOfAUsedYear() {
        when(statement.update()).thenReturn(1, 0);
        assertThatThrownBy(() -> service.setStart(2027, 10)).isInstanceOf(SettingsException.class)
                .hasMessage("Für 2027 sind schon Nummern vergeben. Der Zähler ist gesperrt.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1_000_000})
    void refusesAStartOutOfRange(int start) {
        assertThatThrownBy(() -> service.setStart(2028, start)).isInstanceOf(SettingsException.class)
                .hasMessage("Die Startnummer liegt zwischen 1 und 999999.");
        verify(statement, never()).update();
    }

    @Test
    void acceptsTheLimitsOfTheStart() {
        when(statement.update()).thenReturn(1);
        service.setStart(2028, 1);
        service.setStart(2028, 999_999);
        verify(statement).param("start", 1);
        verify(statement).param("start", 999_999);
    }

    @Test
    void assignsTheNextNumberOfTheYearOfTheIssueDate() {
        when(counter.single()).thenReturn(7);
        assertThat(service.assign(LocalDate.of(2027, 3, 18))).isEqualTo("2027-07");
        verify(jdbc).sql(NumberService.CREATE_YEAR);
        verify(jdbc).sql(NumberService.TAKE);
        verify(statement, times(2)).param("year", 2027);
        verify(statement).update();
    }
}
