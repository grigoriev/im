package io.github.grigoriev.im.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DamageTest {

    @Test
    void keepsItsProblemsAndSumsThemUp() {
        List<String> problems = new ArrayList<>(List.of("Die Datei fehlt.", "Das Siegel passt nicht."));
        Damage damage = new Damage(5L, "Rechnung 2027-01", "/invoices/5", problems);
        problems.clear();
        assertThat(damage.problems()).hasSize(2);
        assertThat(damage.summary()).isEqualTo("Rechnung 2027-01: Die Datei fehlt. Das Siegel passt nicht.");
        List<Damage> found = new ArrayList<>(List.of(damage));
        IntegrityMonitor.Result result = new IntegrityMonitor.Result(Instant.EPOCH, found);
        found.clear();
        assertThat(result.damaged()).containsExactly(damage);
        assertThat(result.intact()).isFalse();
        assertThat(new IntegrityMonitor.Result(Instant.EPOCH, List.of()).intact()).isTrue();
    }
}
