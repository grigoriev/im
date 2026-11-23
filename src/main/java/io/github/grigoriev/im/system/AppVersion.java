package io.github.grigoriev.im.system;

import java.time.Instant;

/**
 * The version of the running app, from {@code META-INF/build-info.properties}.
 *
 * @param number the version, for example {@code 0.1.12} or {@code 0.1.13-SNAPSHOT}; {@code null} for a build without
 *     build info, such as a start from the IDE
 * @param builtAt the time of the build, {@code null} without build info
 */
public record AppVersion(String number, Instant builtAt) {

    static final String RELEASES = "https://github.com/grigoriev/im/releases/tag/v";

    /**
     * Tells whether this is a released version, with its notes on GitHub.
     *
     * @return {@code true} for a release, {@code false} for a snapshot or a build without build info
     */
    public boolean release() {
        return number != null && !number.endsWith("-SNAPSHOT");
    }

    /**
     * Returns the release notes of this version.
     *
     * @return the URL of the release on GitHub, {@code null} unless {@link #release()}
     */
    public String releaseUrl() {
        return release() ? RELEASES + number : null;
    }
}
