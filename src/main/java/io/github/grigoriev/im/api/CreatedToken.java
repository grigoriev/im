package io.github.grigoriev.im.api;

/**
 * A new token with its secret. The secret exists only here: the page shows it once.
 *
 * @param token the stored token
 * @param secret the token to send, {@code im_} and 43 characters
 */
public record CreatedToken(ApiToken token, String secret) {

    /** Leaves the secret out of logs and error messages. */
    @Override
    public String toString() {
        return "CreatedToken[token=" + token + "]";
    }
}
