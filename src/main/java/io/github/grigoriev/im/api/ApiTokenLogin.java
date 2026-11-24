package io.github.grigoriev.im.api;

import io.github.grigoriev.im.user.AppUser;

/**
 * A valid token with the user it acts for.
 *
 * @param user the user
 * @param token the token
 */
public record ApiTokenLogin(AppUser user, ApiToken token) {
}
