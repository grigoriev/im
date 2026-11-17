package io.github.grigoriev.im.migration;

import io.github.grigoriev.im.integrity.Seal;
import io.github.grigoriev.im.integrity.SealMigration;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The migration of the database, a step of its own before the app starts: {@code java -jar im.jar migrate}. Only this
 * step knows the password of the owner of the tables ({@code IM_DB_OWNER_PASSWORD}), the role that can switch a trigger
 * off. The app gets the password of {@code IM_DB_USER} only, so who gets into the app cannot change an issued invoice.
 * No component scan: the step starts Flyway and the Java migrations, nothing of the app.
 */
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
@Import({Seal.class, SealMigration.class})
public class Migration {

    /** The first argument that starts the migration instead of the app. */
    public static final String COMMAND = "migrate";
    /** The profile of the migration: {@code application-migrate.properties} connects Flyway as the owner. */
    static final String PROFILE = "migrate";

    /**
     * Migrates the database to the schema of this version, then ends.
     *
     * @param args command line arguments, for example {@code --spring.datasource.url=...}
     */
    public static void run(String... args) {
        new SpringApplicationBuilder(Migration.class).web(WebApplicationType.NONE).profiles(PROFILE)
                .bannerMode(Banner.Mode.OFF).run(args).close();
    }
}
