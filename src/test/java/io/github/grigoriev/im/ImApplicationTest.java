package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class ImApplicationTest {

    @Test
    void mainStartsTheSpringApplication() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            String[] args = {"--server.port=0"};
            ImApplication.main(args);
            spring.verify(() -> SpringApplication.run(ImApplication.class, args));
        }
    }

    @Test
    void isAConfigurationClassSpringCanInstantiate() {
        assertThat(new ImApplication()).isInstanceOf(ImApplication.class);
    }
}
