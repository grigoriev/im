package io.github.grigoriev.im;

import io.github.grigoriev.im.migration.Migration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL for the integration tests, the same major version as in production. As in production, the migration runs
 * first as the owner of the tables, then the app connects as {@code im_app}, which may only read and write rows.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

    /** The image. Renovate keeps it on the production major (docs/PLAN.md, section 11, rule 7). */
    // renovate: datasource=docker depName=postgres
    static final String IMAGE = "postgres:18.7-alpine";

    /** The role of the app, created by {@code db/app-role.sql}. */
    static final String APP_USER = "im_app";
    static final String APP_PASSWORD = "test-only";

    @Bean
    PostgreSQLContainer postgres() {
        PostgreSQLContainer postgres = new PostgreSQLContainer(IMAGE).withInitScript("db/app-role.sql");
        postgres.start();
        Owner owner = new Owner(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        owner.migrate(null, null);
        return postgres;
    }

    @Bean
    DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> APP_USER);
            registry.add("spring.datasource.password", () -> APP_PASSWORD);
        };
    }

    /**
     * The owner of the tables, for a test that changes what the app cannot: it plays whoever edits the database.
     *
     * @param postgres the database
     * @return a connection as the owner
     */
    @Bean
    Owner owner(PostgreSQLContainer postgres) {
        return new Owner(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    /**
     * The owner of the tables, who also runs the migration.
     *
     * @param url the database of the tests
     * @param username the owner
     * @param password the password
     */
    public record Owner(String url, String username, String password) {

        /**
         * Connects to the database of the tests.
         *
         * @return a connection as the owner
         */
        public JdbcClient jdbc() {
            return JdbcClient.create(dataSource(null));
        }

        /**
         * Connects to a database on the same server.
         *
         * @param database its name, {@code null} for the database of the tests
         * @return a data source as the owner
         */
        public DriverManagerDataSource dataSource(String database) {
            return new DriverManagerDataSource(database == null ? url
                    : url.replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1"), username, password);
        }

        /**
         * Runs the migration of the app ({@code java -jar im.jar migrate}) on a database of the server.
         *
         * @param database its name, {@code null} for the database of the tests
         * @param key the integrity key of the seals it writes, {@code null} for the key of the tests
         */
        public void migrate(String database, String key) {
            List<String> args = new ArrayList<>(List.of("--spring.datasource.url=" + dataSource(database).getUrl(),
                    "--spring.datasource.username=" + APP_USER, "--spring.flyway.user=" + username,
                    "--spring.flyway.password=" + password, "--spring.jmx.enabled=false"));
            if (key != null) {
                args.add("--im.integrity.key=" + key);
            }
            Migration.run(args.toArray(String[]::new));
        }

        /**
         * Connects to a database on the same server as the role of the app.
         *
         * @param database its name
         * @return a data source as {@code im_app}
         */
        public DriverManagerDataSource appDataSource(String database) {
            DriverManagerDataSource source = dataSource(database);
            source.setUsername(APP_USER);
            source.setPassword(APP_PASSWORD);
            return source;
        }
    }
}
