package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.github.grigoriev.im.migration.Migration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class ImApplicationTest {

    @Test
    void mainStartsTheSpringApplication() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class);
                MockedStatic<Migration> migration = mockStatic(Migration.class)) {
            String[] args = {"--server.port=0"};
            ImApplication.main(args);
            spring.verify(() -> SpringApplication.run(ImApplication.class, args));
            String[] none = {};
            ImApplication.main(none);
            spring.verify(() -> SpringApplication.run(ImApplication.class, none));
            migration.verifyNoInteractions();
        }
    }

    @Test
    void mainStartsTheMigrationWithTheFirstArgumentMigrate() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class);
                MockedStatic<Migration> migration = mockStatic(Migration.class)) {
            ImApplication.main(new String[] {"migrate", "--spring.datasource.url=jdbc:postgresql://db/im"});
            migration.verify(() -> Migration.run("--spring.datasource.url=jdbc:postgresql://db/im"));
            ImApplication.main(new String[] {"migrate"});
            migration.verify(Migration::run);
            spring.verifyNoInteractions();
        }
    }

    @Test
    void isAConfigurationClassSpringCanInstantiate() {
        assertThat(new ImApplication()).isInstanceOf(ImApplication.class);
    }
}
