package io.github.grigoriev.im.audit;

import java.time.Clock;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads the audit log. An entry joins the transaction of the change it records: if the change
 * rolls back, so does its entry.
 */
@Service
public class AuditService {

    /** The name of the app itself, for example when it creates the first admin. */
    static final String SYSTEM = "system";

    /** Maximum length of a login name and of a detail, as in the database. */
    static final int MAX_USERNAME = 64;
    static final int MAX_DETAIL = 500;

    private final AuditRepository events;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param events the audit log
     * @param clock source of the time
     */
    public AuditService(AuditRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /**
     * Records an event of the logged-in user.
     *
     * @param action what happened
     * @param detail what it concerned; never a password or a token
     */
    @Transactional
    public void add(AuditAction action, String detail) {
        save(currentUser(), action, detail);
    }

    /**
     * Records an event of a given user, for example a login.
     *
     * @param username who did it
     * @param action what happened
     * @param detail what it concerned; never a password or a token
     */
    @Transactional
    public void add(String username, AuditAction action, String detail) {
        save(username, action, detail);
    }

    /**
     * Returns the newest entries.
     *
     * @return at most 200 entries, newest first
     */
    public List<AuditEvent> latest() {
        return events.findTop200ByOrderByAtDescIdDesc();
    }

    private void save(String username, AuditAction action, String detail) {
        events.save(new AuditEvent(null, clock.instant(), cut(username, MAX_USERNAME), action, cut(detail, MAX_DETAIL)));
    }

    static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        return authentication.getName();
    }

    static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
