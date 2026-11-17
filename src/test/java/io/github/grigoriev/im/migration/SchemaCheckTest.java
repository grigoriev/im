package io.github.grigoriev.im.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.ErrorDetails;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Test;

class SchemaCheckTest {

    private final Flyway flyway = mock(Flyway.class);

    @Test
    void startsOnTheCurrentSchemaWithoutMigrating() {
        when(flyway.validateWithResult()).thenReturn(new ValidateResult("12", "im", null, true, 33, List.of(), List.of()));
        new SchemaCheck().migrate(flyway);
        verify(flyway, never()).migrate();
    }

    @Test
    void refusesToStartOnAnOlderSchema() {
        ErrorDetails pending = new ErrorDetails(CoreErrorCode.VALIDATE_ERROR,
                "Detected resolved migration not applied: 33");
        when(flyway.validateWithResult()).thenReturn(new ValidateResult("12", "im",
                new ErrorDetails(CoreErrorCode.VALIDATE_ERROR, "Migrations have failed validation"), false, 32,
                List.of(new ValidateOutput("33", "audit kept", "V33__audit_kept.sql", pending)), List.of()));
        SchemaCheck check = new SchemaCheck();
        assertThatThrownBy(() -> check.migrate(flyway)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The database is not on the schema of this version. Run the migration first: "
                        + "java -jar im.jar migrate. Detected resolved migration not applied: 33");
        verify(flyway, never()).migrate();
    }
}
