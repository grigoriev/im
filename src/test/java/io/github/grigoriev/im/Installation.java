package io.github.grigoriev.im;

import java.io.IOException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * The whole app on a database of its own on the test server, wired as in production: the migration runs as the owner,
 * then the app connects as {@code im_app}. For a test that needs a second installation, such as the target of an import or a
 * database migrated from an older version.
 */
public final class Installation implements AutoCloseable {

    private final ConfigurableApplicationContext context;

    private Installation(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /**
     * Starts the app on an existing database. The migration brings it to the latest version first.
     *
     * @param owner the owner of the tables on the test server
     * @param database the name of the database
     * @param key the integrity key of the installation
     * @return the running app
     */
    public static Installation start(TestDatabase.Owner owner, String database, String key) {
        owner.migrate(database, key);
        // Arguments, since they win over the files of the classpath; default properties would lose to them.
        return new Installation(new SpringApplicationBuilder(ImApplication.class)
                .initializers(starting -> starting.getBeanFactory().registerSingleton("skipTestDatabase",
                        new SkipTestDatabase()))
                .run("--spring.datasource.url=" + owner.dataSource(database).getUrl(),
                        "--spring.datasource.username=" + TestDatabase.APP_USER,
                        "--spring.datasource.password=" + TestDatabase.APP_PASSWORD,
                        "--im.integrity.key=" + key, "--server.port=0", "--spring.jmx.enabled=false"));
    }

    /**
     * Returns a bean of the installation.
     *
     * @param <T> its type
     * @param type its type
     * @return the bean
     */
    public <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    @Override
    public void close() {
        context.close();
    }

    /** Keeps the test database of the tests out of the second app: it would start a container of its own. */
    private static final class SkipTestDatabase extends TypeExcludeFilter {

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) throws IOException {
            return reader.getClassMetadata().getClassName().equals(TestDatabase.class.getName());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SkipTestDatabase;
        }

        @Override
        public int hashCode() {
            return SkipTestDatabase.class.hashCode();
        }
    }
}
