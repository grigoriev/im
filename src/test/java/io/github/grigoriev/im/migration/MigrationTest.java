package io.github.grigoriev.im.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class MigrationTest {

    @Test
    void migratesWithoutTheWebServerInTheProfileOfTheOwnerAndEnds() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        try (MockedConstruction<SpringApplicationBuilder> builders = mockConstruction(SpringApplicationBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF),
                (builder, construction) -> {
                    assertThat(construction.arguments().getFirst()).isEqualTo(new Class<?>[] {Migration.class});
                    when(builder.run(any(String[].class))).thenReturn(context);
                })) {
            Migration.run("--spring.datasource.url=jdbc:postgresql://db/im");
            List<SpringApplicationBuilder> constructed = builders.constructed();
            assertThat(constructed).hasSize(1);
            SpringApplicationBuilder builder = constructed.getFirst();
            verify(builder).web(WebApplicationType.NONE);
            verify(builder).profiles("migrate");
            verify(builder).bannerMode(Banner.Mode.OFF);
            verify(builder).run("--spring.datasource.url=jdbc:postgresql://db/im");
            verify(context).close();
        }
    }

    @Test
    void isASourceSpringCanInstantiate() {
        assertThat(BeanUtils.instantiateClass(Migration.class)).isInstanceOf(Migration.class);
    }
}
