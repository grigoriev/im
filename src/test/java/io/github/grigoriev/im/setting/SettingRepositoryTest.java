package io.github.grigoriev.im.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class SettingRepositoryTest {

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec statement = mock(StatementSpec.class);
    @SuppressWarnings("unchecked")
    private final MappedQuerySpec<String> query = mock(MappedQuerySpec.class);
    private final SettingRepository settings = new SettingRepository(jdbc);

    @BeforeEach
    void statement() {
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.query(String.class)).thenReturn(query);
    }

    @ParameterizedTest
    @CsvSource({
        "INVOICE_NUMBER_PATTERN, invoice-number-pattern",
        "MAIL_SUBJECT, mail-subject",
        "MAIL_BODY, mail-body",
        "REMINDER_SUBJECT, reminder-subject",
        "REMINDER_BODY, reminder-body",
        "PROCEDURE_VALID_FROM, procedure-valid-from",
        "PROCEDURE_EXPENSES, procedure-expenses",
        "PROCEDURE_ARCHIVE, procedure-archive"
    })
    void keepsTheStoredKeys(SettingKey key, String stored) {
        assertThat(key.stored()).isEqualTo(stored);
    }

    @Test
    void readsAValueByItsKey() {
        when(query.optional()).thenReturn(Optional.of("RE-{YYYY}-{NNN}"));
        assertThat(settings.read(SettingKey.INVOICE_NUMBER_PATTERN)).contains("RE-{YYYY}-{NNN}");
        verify(jdbc).sql("SELECT value FROM app_setting WHERE key = :key");
        verify(statement).param("key", "invoice-number-pattern");
    }

    @Test
    void readsNothingForAKeyWithoutValue() {
        when(query.optional()).thenReturn(Optional.empty());
        assertThat(settings.read(SettingKey.MAIL_BODY)).isEmpty();
    }

    @Test
    void writesOrReplacesAValue() {
        settings.write(SettingKey.MAIL_SUBJECT, "Rechnung {Rechnungsnummer}");
        verify(jdbc).sql("""
                INSERT INTO app_setting (key, value) VALUES (:key, :value)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value""");
        verify(statement).param("key", "mail-subject");
        verify(statement).param("value", "Rechnung {Rechnungsnummer}");
        verify(statement).update();
    }

    @Test
    void writesAnEmptyValueAsNull() {
        settings.write(SettingKey.PROCEDURE_ARCHIVE, null);
        verify(statement).param("key", "procedure-archive");
        verify(statement).param("value", null);
        verify(statement).update();
    }

    @Test
    void resetsAValue() {
        settings.reset(SettingKey.REMINDER_BODY);
        verify(jdbc).sql("DELETE FROM app_setting WHERE key = :key");
        verify(statement).param("key", "reminder-body");
        verify(statement).update();
    }
}
