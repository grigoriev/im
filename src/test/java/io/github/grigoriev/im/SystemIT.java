package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.system.SystemInfo;
import io.github.grigoriev.im.system.SystemService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The page "System" against PostgreSQL: the app, connected as {@code im_app}, reads the schema and the server. */
@SpringBootTest
@Import(TestDatabase.class)
class SystemIT {

    private static final Pattern MIGRATION = Pattern.compile("V(\\d+)__.*\\.sql");

    @Autowired
    private SystemService system;

    @Test
    void namesTheLastMigrationAndThePostgresVersion() throws IOException {
        SystemInfo info = system.info();
        assertThat(info.schema()).isEqualTo(String.valueOf(lastMigration()));
        assertThat(info.database()).matches("\\d+(\\.\\d+)?.*");
    }

    private static int lastMigration() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            return files.map(file -> MIGRATION.matcher(file.getFileName().toString())).filter(Matcher::matches)
                    .mapToInt(matcher -> Integer.parseInt(matcher.group(1))).max().orElseThrow();
        }
    }
}
