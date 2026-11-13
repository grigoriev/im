package io.github.grigoriev.im.seller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsExceptionTest {

    @Test
    void keepsACopyOfTheProblemsAndJoinsThemAsMessage() {
        List<String> problems = new ArrayList<>(List.of("Eins.", "Zwei."));
        SettingsException exception = new SettingsException(problems);
        problems.add("Drei.");
        assertThat(exception.problems()).containsExactly("Eins.", "Zwei.");
        assertThat(exception.getMessage()).isEqualTo("Eins. Zwei.");
    }
}
