package io.github.grigoriev.im.user;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates users and resets their passwords. */
@Service
public class UserService {

    /** Login names: 3 to 32 lowercase letters, digits, dots, hyphens or underscores. */
    static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,32}");

    /** Minimum password length. A passkey is the usual login; the password is the fallback. */
    static final int MIN_PASSWORD = 12;

    /** Maximum length of the display name, as in the database. */
    static final int MAX_DISPLAY_NAME = 128;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final PersistentTokenRepository rememberMe;
    private final Clock clock;
    private final AuditService audit;
    private final PasskeyService passkeys;

    /**
     * Creates the service.
     *
     * @param users the user store
     * @param encoder encodes passwords
     * @param rememberMe the "Angemeldet bleiben" tokens
     * @param clock source of the creation time
     * @param audit the audit log
     * @param passkeys forgets the passkeys of a deleted user
     */
    public UserService(AppUserRepository users, PasswordEncoder encoder, PersistentTokenRepository rememberMe,
            Clock clock, AuditService audit, PasskeyService passkeys) {
        this.users = users;
        this.encoder = encoder;
        this.rememberMe = rememberMe;
        this.clock = clock;
        this.audit = audit;
        this.passkeys = passkeys;
    }

    /**
     * Lists all users by login name.
     *
     * @return the users
     */
    public List<AppUser> list() {
        return users.findAllByOrderByUsername();
    }

    /**
     * Returns a user.
     *
     * @param id the user
     * @return the user
     * @throws UserException if the user does not exist
     */
    public AppUser get(long id) {
        return users.findById(id).orElseThrow(() -> new UserException("Diesen Benutzer gibt es nicht."));
    }

    /**
     * Finds a user by login name.
     *
     * @param username the login name
     * @return the user, empty if there is none
     */
    public Optional<AppUser> find(String username) {
        return users.findByUsername(username);
    }

    /**
     * Creates a user.
     *
     * @param username login name
     * @param displayName name shown in the app
     * @param password the password
     * @param role the role
     * @return the saved user
     * @throws UserException if a value is not allowed or the login name is taken
     */
    @Transactional
    public AppUser create(String username, String displayName, String password, Role role) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new UserException("Der Benutzername hat 3 bis 32 Zeichen: Kleinbuchstaben, Ziffern, Punkt, Bindestrich, Unterstrich.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new UserException("Bitte einen Namen angeben.");
        }
        if (displayName.strip().length() > MAX_DISPLAY_NAME) {
            throw new UserException("Der Name hat höchstens " + MAX_DISPLAY_NAME + " Zeichen.");
        }
        checkPassword(password);
        if (users.findByUsername(username).isPresent()) {
            throw new UserException("Der Benutzername „" + username + "“ ist schon vergeben.");
        }
        AppUser saved = users.save(new AppUser(null, username, displayName.strip(), encoder.encode(password), role, true,
                clock.instant()));
        audit.add(AuditAction.USER_CREATED, username + " (" + role + ")");
        return saved;
    }

    /**
     * Sets a new password and ends every "Angemeldet bleiben" of the user: a reset usually follows a
     * leaked password or a lost device.
     *
     * @param id the user
     * @param password the new password
     * @return the changed user
     * @throws UserException if the user does not exist or the password is too short
     */
    @Transactional
    public AppUser resetPassword(long id, String password) {
        checkPassword(password);
        AppUser user = get(id);
        AppUser saved = users.save(user.withPasswordHash(encoder.encode(password)));
        rememberMe.removeUserTokens(saved.username());
        audit.add(AuditAction.PASSWORD_RESET, saved.username());
        return saved;
    }

    /**
     * Changes the password of the logged-in user, after a check of the current one. Other devices
     * lose their "Angemeldet bleiben".
     *
     * @param username the login name
     * @param current the current password
     * @param password the new password
     * @throws UserException if the current password is wrong or the new one too short
     */
    @Transactional
    public void changePassword(String username, String current, String password) {
        AppUser user = confirmed(username, current, "Das aktuelle Passwort stimmt nicht.");
        checkPassword(password);
        users.save(user.withPasswordHash(encoder.encode(password)));
        rememberMe.removeUserTokens(username);
        audit.add(username, AuditAction.PASSWORD_CHANGED, "");
    }

    /**
     * Checks the password of the logged-in user, for example before a new passkey.
     *
     * @param username the login name
     * @param password the password
     * @throws UserException if the password is wrong
     */
    public void confirmPassword(String username, String password) {
        confirmed(username, password, "Das Passwort stimmt nicht.");
    }

    private AppUser confirmed(String username, String password, String wrong) {
        AppUser user = users.findByUsername(username).orElseThrow(() -> new UserException("Diesen Benutzer gibt es nicht."));
        if (password == null || !encoder.matches(password, user.passwordHash())) {
            throw new UserException(wrong);
        }
        return user;
    }

    /**
     * Deletes a user with the passkeys and the "Angemeldet bleiben" of the user. The audit log keeps the login name in
     * its entries. Nobody deletes the own account, and the last admin stays.
     *
     * @param id the user
     * @return the deleted user
     * @throws UserException if the user does not exist, is the logged-in one or the last admin
     */
    @Transactional
    public AppUser delete(long id) {
        AppUser user = get(id);
        if (user.username().equals(AuditService.currentUser())) {
            throw new UserException("Das eigene Konto lässt sich nicht löschen.");
        }
        if (user.role() == Role.ADMIN && users.lockAdmins().size() <= 1) {
            throw new UserException("Der letzte Administrator bleibt. Bitte zuerst einen weiteren anlegen.");
        }
        passkeys.forget(user.username());
        rememberMe.removeUserTokens(user.username());
        users.delete(user);
        audit.add(AuditAction.USER_DELETED, user.username() + " (" + user.role() + ")");
        return user;
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new UserException("Das Passwort hat mindestens " + MIN_PASSWORD + " Zeichen.");
        }
    }
}
