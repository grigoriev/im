package io.github.grigoriev.im.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Handles a form that outlived its session. The CSRF token lives in the session, so after the timeout
 * an open page posts an old token.
 *
 * <ul>
 *   <li>A stale logout still logs out: it ends the "Angemeldet bleiben" token, clears its cookie and
 *       the session. Otherwise the remember-me cookie would log the user in again on the next visit.
 *   <li>Any other stale form leads to the login page with a note that the session expired.
 *   <li>Every other denial keeps the 403 page.
 * </ul>
 */
@Component
public class StaleFormHandler implements AccessDeniedHandler {

    static final String REMEMBER_ME_COOKIE = "remember-me";

    private final PersistentTokenRepository tokens;
    private final AccessDeniedHandler forbidden = new AccessDeniedHandlerImpl();

    /**
     * Creates the handler.
     *
     * @param tokens the "Angemeldet bleiben" tokens
     */
    public StaleFormHandler(PersistentTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
            throws IOException, ServletException {
        if (!(denied instanceof CsrfException)) {
            forbidden.handle(request, response, denied);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && "/logout".equals(path)) {
            endRememberMe(request, response);
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + "/login?abgemeldet");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?abgelaufen");
    }

    private void endRememberMe(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = WebUtils.getCookie(request, REMEMBER_ME_COOKIE);
        if (cookie == null) {
            return;
        }
        String series = series(cookie.getValue());
        PersistentRememberMeToken token = series == null ? null : tokens.getTokenForSeries(series);
        if (token != null) {
            tokens.removeUserTokens(token.getUsername());
        }
        Cookie clear = new Cookie(REMEMBER_ME_COOKIE, "");
        clear.setMaxAge(0);
        clear.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        clear.setHttpOnly(true);
        clear.setSecure(request.isSecure());
        response.addCookie(clear);
    }

    /**
     * Reads the series from a remember-me cookie: Base64 without padding of {@code series:token}, each
     * part URL-encoded, as Spring Security writes it.
     *
     * @return the series, or {@code null} for a value that is no such cookie
     */
    static String series(String value) {
        try {
            String padded = value + "=".repeat((4 - value.length() % 4) % 4);
            String decoded = new String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon <= 0 ? null : URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
