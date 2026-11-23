package io.github.grigoriev.im.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppVersionTest {

    private static final Instant BUILT = Instant.parse("2026-11-23T05:24:00Z");

    @Test
    void aReleaseLinksToItsNotes() {
        AppVersion version = new AppVersion("0.1.12", BUILT);
        assertThat(version.release()).isTrue();
        assertThat(version.releaseUrl()).isEqualTo("https://github.com/grigoriev/im/releases/tag/v0.1.12");
    }

    @Test
    void aSnapshotHasNoNotes() {
        AppVersion version = new AppVersion("0.1.13-SNAPSHOT", BUILT);
        assertThat(version.release()).isFalse();
        assertThat(version.releaseUrl()).isNull();
    }

    @Test
    void aBuildWithoutBuildInfoHasNoNotes() {
        AppVersion version = new AppVersion(null, null);
        assertThat(version.release()).isFalse();
        assertThat(version.releaseUrl()).isNull();
    }
}
