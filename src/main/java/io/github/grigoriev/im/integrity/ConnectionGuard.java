package io.github.grigoriev.im.integrity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Refuses to start while the app connects to the database as a role that could switch the triggers off: a superuser,
 * or the owner of a table, or a member of the owner. Flyway migrates as the owner ({@code IM_DB_OWNER}); the app
 * connects as a role that may only read and write rows ({@code IM_DB_USER}).
 */
@Component
public class ConnectionGuard implements ApplicationRunner {

    static final String MIGHTY = """
            SELECT rolsuper OR EXISTS (SELECT 1 FROM pg_tables
                                        WHERE schemaname = current_schema()
                                          AND pg_has_role(current_user, tableowner, 'USAGE'))
              FROM pg_roles WHERE rolname = current_user""";
    static final String REFUSED = "The app connects to the database as a superuser or as the owner of its tables. "
            + "Set IM_DB_USER to a role that may only read and write rows, and IM_DB_OWNER to the owner for Flyway.";

    private final JdbcClient jdbc;

    /**
     * Creates the guard.
     *
     * @param jdbc the connection of the app
     */
    public ConnectionGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Checks the role of the connection.
     *
     * @param args not used
     * @throws IllegalStateException if the role could change tables or triggers
     */
    @Override
    public void run(ApplicationArguments args) {
        if (jdbc.sql(MIGHTY).query(Boolean.class).single()) {
            throw new IllegalStateException(REFUSED);
        }
    }
}
