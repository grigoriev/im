package io.github.grigoriev.im.api;

import io.github.grigoriev.im.user.AppUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs a request of the API in with its bearer token. Without a valid token it answers 401: the API knows no session
 * and no login page. It refuses an upload at once: the API reads JSON only, and the servlet would store a multipart
 * body on disk before any check. Not a bean, so that it runs in the filter chain of the API only.
 */
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final ApiTokenService tokens;

    /**
     * Creates the filter.
     *
     * @param tokens checks the tokens
     */
    public ApiTokenFilter(ApiTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getContentType();
        if (type != null && type.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            ApiProblems.write(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Die Schnittstelle nimmt nur JSON an.");
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        Optional<ApiTokenLogin> login = header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                ? tokens.authenticate(header.substring(BEARER.length()).strip())
                : Optional.empty();
        if (login.isEmpty()) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            ApiProblems.write(response, HttpStatus.UNAUTHORIZED, "Bitte einen gültigen API-Token senden.");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(login.get()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    /**
     * Returns the login of a token: the role of its user and the scope of the token.
     *
     * @param login the token with its user
     * @return the authentication, its details name the token
     */
    static UsernamePasswordAuthenticationToken authentication(ApiTokenLogin login) {
        AppUser user = login.user();
        List<GrantedAuthority> authorities = new ArrayList<>(List.of(new SimpleGrantedAuthority(user.role().authority()),
                new SimpleGrantedAuthority(ApiTokenScope.READ_AUTHORITY)));
        if (login.token().scope() == ApiTokenScope.WRITE) {
            authorities.add(new SimpleGrantedAuthority(ApiTokenScope.WRITE_AUTHORITY));
        }
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(user.username(), null, authorities);
        authentication.setDetails(new ApiTokenDetails(login.token().id(), login.token().name()));
        return authentication;
    }
}
