package io.github.grigoriev.im.system;

import java.time.Instant;

/**
 * What runs, for the page "System" of the admin.
 *
 * @param version the version of the app
 * @param schema the version of the last migration of the database, for example {@code 36}
 * @param database the version of PostgreSQL
 * @param java the version of Java
 * @param startedAt the start of the app
 */
public record SystemInfo(AppVersion version, String schema, String database, String java, Instant startedAt) {
}
