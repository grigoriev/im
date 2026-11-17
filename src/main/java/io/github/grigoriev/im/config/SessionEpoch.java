package io.github.grigoriev.im.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The moment from which a session counts. An import replaces the users, so it ends every session that started before
 * it, in every browser: the filter ahead of Spring Security drops such a session, and its user logs in anew. A deleted
 * user, or one with a new password, loses the sessions of that user only. Who changes the own password keeps the
 * session of the change.
 */
@Component
public class SessionEpoch {

    /** Marks the session that survives the end of the other sessions of its user, with the moment of that end. */
    static final String KEPT = SessionEpoch.class.getName() + ".KEPT";

    private final AtomicLong since = new AtomicLong();
    /** The users whose sessions end, with the moment: a deleted user loses every session that started before. */
    private final Map<String, Long> ended = new ConcurrentHashMap<>();
    private final Clock clock;

    /**
     * Creates the epoch. At the start every session counts.
     *
     * @param clock the time of a new epoch
     */
    public SessionEpoch(Clock clock) {
        this.clock = clock;
    }

    /** Ends every session that exists now. */
    public void renew() {
        since.set(clock.millis());
    }

    /**
     * Ends every session of a user that exists now, for example after the admin deleted the user.
     *
     * @param username the login name
     */
    public void end(String username) {
        ended.put(username, clock.millis());
    }

    /**
     * Ends every other session of a user that exists now, for example after the user changed the own password. The
     * given session stays, also when a new login gives it another ID.
     *
     * @param username the login name
     * @param kept the session that stays
     */
    public void endOthers(String username, HttpSession kept) {
        long at = clock.millis();
        kept.setAttribute(KEPT, at);
        ended.put(username, at);
    }

    /**
     * Tells whether a session of a user started before the user's sessions ended, or in the same millisecond.
     *
     * @param username the login name of the session
     * @param creationTime the start of the session, in milliseconds
     * @return {@code true} if the session no longer counts
     */
    public boolean ended(String username, long creationTime) {
        Long at = ended.get(username);
        return at != null && creationTime <= at;
    }

    /**
     * Tells whether a session started before the epoch.
     *
     * @param creationTime the start of the session, in milliseconds
     * @return {@code true} if the session no longer counts
     */
    public boolean outdated(long creationTime) {
        return creationTime < since.get();
    }

    private boolean endedUser(HttpSession session) {
        if (session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
                        instanceof SecurityContext context
                && context.getAuthentication() instanceof Authentication authentication) {
            Long at = ended.get(authentication.getName());
            return at != null && session.getCreationTime() <= at && !at.equals(session.getAttribute(KEPT));
        }
        return false;
    }

    /**
     * Returns the filter that drops an outdated session before Spring Security reads it.
     *
     * @return the filter
     */
    public OncePerRequestFilter filter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                HttpSession session = request.getSession(false);
                if (session != null && (outdated(session.getCreationTime()) || endedUser(session))) {
                    session.invalidate();
                }
                chain.doFilter(request, response);
            }
        };
    }
}
