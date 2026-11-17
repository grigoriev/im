package io.github.grigoriev.im.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A login of the last minutes, with the password or a passkey, or the password confirmed in this session. A new passkey
 * needs one: who holds a stolen session or an unlocked device cannot add an own passkey and keep access after a
 * password reset. "Angemeldet bleiben" does not count.
 */
@Component
public class RecentLogin {

    /** How long a login or a confirmation counts. */
    static final Duration VALID = Duration.ofMinutes(15);
    /**
     * The registration endpoints of Spring Security: they answer before the access rules apply. The pattern matches the
     * decoded path, as the WebAuthn filters do: {@code /webauthn/%72egister} is the same endpoint.
     */
    private static final RequestMatcher REGISTRATION =
            PathPatternRequestMatcher.withDefaults().matcher("/webauthn/register/**");
    /** The moment the user confirmed the password in this session. */
    static final String CONFIRMED = RecentLogin.class.getName() + ".CONFIRMED";

    private final Clock clock;

    /**
     * Creates the check.
     *
     * @param clock the time now
     */
    public RecentLogin(Clock clock) {
        this.clock = clock;
    }

    /**
     * Tells whether the user logged in or confirmed the password in the last 15 minutes.
     *
     * @param authentication the login of the request, {@code null} if none
     * @param session the session of the request, {@code null} if none
     * @return {@code true} if a passkey may be added now
     */
    public boolean holds(Authentication authentication, HttpSession session) {
        Instant since = clock.instant().minus(VALID);
        boolean login = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority instanceof FactorGrantedAuthority factor
                        && !factor.getIssuedAt().isBefore(since));
        return login || session != null && session.getAttribute(CONFIRMED) instanceof Instant at
                && !at.isBefore(since);
    }

    /**
     * Records that the user confirmed the password in this session.
     *
     * @param session the session
     */
    public void confirm(HttpSession session) {
        session.setAttribute(CONFIRMED, clock.instant());
    }

    /**
     * Returns the filter that refuses the passkey registration without a recent login. It runs ahead of the WebAuthn
     * filters of Spring Security, which answer the registration themselves.
     *
     * @return the filter
     */
    public OncePerRequestFilter filter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                if (REGISTRATION.matches(request) && !holds(SecurityContextHolder.getContext().getAuthentication(),
                        request.getSession(false))) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }
}
