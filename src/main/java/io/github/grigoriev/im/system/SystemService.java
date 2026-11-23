package io.github.grigoriev.im.system;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Names the version of the app for every page and what runs for the admin. */
@Service
public class SystemService {

    static final String SCHEMA = "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC "
            + "LIMIT 1";
    static final String DATABASE = "SHOW server_version";

    private final AppVersion version;
    private final JdbcClient jdbc;
    private final Instant startedAt;

    /**
     * Creates the service and notes the start of the app.
     *
     * @param build the build info, absent in a build without it
     * @param jdbc the database
     * @param clock the clock
     */
    public SystemService(ObjectProvider<BuildProperties> build, JdbcClient jdbc, Clock clock) {
        BuildProperties properties = build.getIfAvailable();
        this.version = properties == null ? new AppVersion(null, null)
                : new AppVersion(properties.getVersion(), properties.getTime());
        this.jdbc = jdbc;
        this.startedAt = clock.instant();
    }

    /**
     * Returns the version of the running app.
     *
     * @return the version
     */
    public AppVersion version() {
        return version;
    }

    /**
     * Collects what runs.
     *
     * @return version, schema, PostgreSQL, Java and the start
     */
    public SystemInfo info() {
        String schema = jdbc.sql(SCHEMA).query(String.class).single();
        String database = jdbc.sql(DATABASE).query(String.class).single();
        return new SystemInfo(version, schema, database, Runtime.version().toString(), startedAt);
    }
}
