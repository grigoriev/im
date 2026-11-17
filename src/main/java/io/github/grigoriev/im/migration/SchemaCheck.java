package io.github.grigoriev.im.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.ValidateResult;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;

/**
 * Lets the app start only on the schema of its version. The app connects as {@code IM_DB_USER}, which cannot migrate:
 * the step {@code java -jar im.jar migrate} does that as the owner ({@link Migration}).
 */
@Component
public class SchemaCheck implements FlywayMigrationStrategy {

    static final String MIGRATE_FIRST = "The database is not on the schema of this version. Run the migration first: "
            + "java -jar im.jar migrate. ";

    /**
     * Checks the schema instead of migrating it.
     *
     * @param flyway Flyway on the connection of the app
     * @throws IllegalStateException if a migration is missing or differs
     */
    @Override
    public void migrate(Flyway flyway) {
        ValidateResult result = flyway.validateWithResult();
        if (!result.validationSuccessful) {
            throw new IllegalStateException(MIGRATE_FIRST + result.getAllErrorMessages());
        }
    }
}
