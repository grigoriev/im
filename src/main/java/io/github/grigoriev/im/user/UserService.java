package io.github.grigoriev.im.user;

import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates users and resets their passwords. */
@Service
public class UserService {

    /** Login names: 3 to 32 lowercase letters, digits, dots, hyphens or underscores. */
    static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,32}");

    /** Minimum password length. A passkey is the usual login; the password is the fallback. */
    static final int MIN_PASSWORD = 12;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param users the user store
     * @param encoder encodes passwords
     * @param clock source of the creation time
     */
    public UserService(AppUserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
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
        checkPassword(password);
        if (users.findByUsername(username).isPresent()) {
            throw new UserException("Der Benutzername „" + username + "“ ist schon vergeben.");
        }
        return users.save(new AppUser(null, username, displayName.strip(), encoder.encode(password), role, true,
                clock.instant()));
    }

    /**
     * Sets a new password.
     *
     * @param id the user
     * @param password the new password
     * @return the changed user
     * @throws UserException if the user does not exist or the password is too short
     */
    @Transactional
    public AppUser resetPassword(long id, String password) {
        checkPassword(password);
        AppUser user = users.findById(id).orElseThrow(() -> new UserException("Diesen Benutzer gibt es nicht."));
        return users.save(user.withPasswordHash(encoder.encode(password)));
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new UserException("Das Passwort hat mindestens " + MIN_PASSWORD + " Zeichen.");
        }
    }
}
