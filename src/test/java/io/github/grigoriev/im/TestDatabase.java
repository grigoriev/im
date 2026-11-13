package io.github.grigoriev.im;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL for the integration tests, the same major version as in production. */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

    /** The image, kept equal to the production database (docs/PLAN.md, section 11). */
    // renovate: datasource=docker depName=postgres
    static final String IMAGE = "postgres:18.7-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
