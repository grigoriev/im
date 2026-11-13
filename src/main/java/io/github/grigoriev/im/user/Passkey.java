package io.github.grigoriev.im.user;

import java.time.Instant;

/**
 * A passkey as the account page shows it.
 *
 * @param id credential ID, Base64URL
 * @param label name the user gave it, for example "MacBook Air"
 * @param created time of registration
 * @param lastUsed time of the last login, {@code null} before the first
 */
public record Passkey(String id, String label, Instant created, Instant lastUsed) {
}
