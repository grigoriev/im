package io.github.grigoriev.im.api;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.integrity.Seal;
import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, lists, deletes and checks the personal API tokens (docs/adr/0007-api-tokens-and-mcp.md). A token is
 * {@code im_} and 32 random bytes in Base64URL; the table keeps its SHA-256 hash.
 */
@Service
public class ApiTokenService {

    /** The start of every token: a scanner for secrets recognizes it. */
    static final String PREFIX = "im_";
    /** Random bytes of a token. */
    static final int BYTES = 32;
    /** The length of a token: the prefix and the bytes in Base64URL without padding. */
    static final int LENGTH = PREFIX.length() + 43;
    /** Maximum length of a token name, as in the database. */
    static final int MAX_NAME = 64;
    /** The longest life of a token. */
    static final int MAX_DAYS = 365;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiTokenRepository tokens;
    private final AppUserRepository users;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param tokens the token store
     * @param users the user store
     * @param audit the audit log
     * @param clock source of the time
     */
    public ApiTokenService(ApiTokenRepository tokens, AppUserRepository users, AuditService audit, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Lists the tokens of a user, newest first.
     *
     * @param username the login name
     * @return the tokens, empty for an unknown user
     */
    public List<ApiToken> list(String username) {
        return users.findByUsername(username).map(user -> tokens.findAllByUserIdOrderByCreatedAtDescIdDesc(user.id()))
                .orElse(List.of());
    }

    /**
     * Creates a token for a user.
     *
     * @param username the login name
     * @param name the name of the token
     * @param scope what it may do
     * @param days its life in days, 1 to 365
     * @return the token with its secret, which exists only here
     * @throws ApiTokenException if an input is wrong
     */
    @Transactional
    public CreatedToken create(String username, String name, ApiTokenScope scope, int days) {
        String label = name == null ? "" : name.strip();
        if (label.isEmpty() || label.length() > MAX_NAME) {
            throw new ApiTokenException("Bitte einen Namen mit höchstens " + MAX_NAME + " Zeichen angeben.");
        }
        if (scope == null) {
            throw new ApiTokenException("Bitte die Rechte des Tokens wählen.");
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new ApiTokenException("Ein Token gilt einen bis " + MAX_DAYS + " Tage.");
        }
        AppUser user = users.findByUsername(username).orElseThrow(() -> new ApiTokenException("Unbekannter Benutzer."));
        String secret = secret();
        Instant now = clock.instant();
        ApiToken saved = tokens.save(new ApiToken(null, user.id(), label, hash(secret), scope, now,
                now.plus(Duration.ofDays(days)), null));
        audit.add(AuditAction.API_TOKEN_CREATED, label + ", " + scope.label() + ", " + days + " Tage");
        return new CreatedToken(saved, secret);
    }

    /**
     * Deletes a token of a user. It stops working at once.
     *
     * @param username the login name
     * @param id the ID of the token
     * @return the deleted token
     * @throws ApiTokenException if the user has no such token
     */
    @Transactional
    public ApiToken delete(String username, long id) {
        ApiToken token = tokens.findById(id)
                .filter(found -> users.findByUsername(username).map(AppUser::id).filter(found.userId()::equals)
                        .isPresent())
                .orElseThrow(() -> new ApiTokenException("Diesen Token gibt es nicht."));
        tokens.deleteById(id);
        audit.add(AuditAction.API_TOKEN_DELETED, token.name());
        return token;
    }

    /**
     * Checks a token of a request and records its use.
     *
     * @param secret the token as sent, may be {@code null}
     * @return the token with its user; empty if the token is unknown, expired or its user disabled
     */
    @Transactional
    public Optional<ApiTokenLogin> authenticate(String secret) {
        if (secret == null || secret.length() != LENGTH || !secret.startsWith(PREFIX)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByTokenHash(hash(secret))
                .filter(token -> token.expiresAt().isAfter(now))
                .flatMap(token -> users.findById(token.userId()).filter(AppUser::enabled)
                        .filter(user -> tokens.markUsed(token.id(), now) == 1)
                        .map(user -> new ApiTokenLogin(user, token.usedAt(now))));
    }

    /**
     * Returns the SHA-256 of a token.
     *
     * @param secret the token
     * @return 64 hex digits
     */
    static String hash(String secret) {
        return Seal.sha256(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String secret() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
