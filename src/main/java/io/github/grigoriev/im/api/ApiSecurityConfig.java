package io.github.grigoriev.im.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

/**
 * The access rules of the API (docs/adr/0007-api-tokens-and-mcp.md). It runs ahead of the rules of the pages: a bearer
 * token only, no session, no login page. Every token reads; a write needs the scope {@code WRITE}.
 */
@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfig {

    /** The URLs of the API. */
    static final String[] PATHS = {"/api/**"};
    /** The answer to a refused request: a token that only reads, or a browser without the CSRF token. */
    static final String FORBIDDEN =
            "Nicht erlaubt: Der API-Token darf nur lesen, oder eine Anfrage mit Cookies hat kein CSRF-Token.";
    /** The methods that change nothing. */
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /**
     * Returns the filter chain of the API.
     *
     * @param http the security builder
     * @param tokens checks the tokens
     * @return the filter chain
     * @throws Exception if the configuration is invalid
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, ApiTokenService tokens) throws Exception {
        return http
                .securityMatcher(PATHS)
                .addFilterBefore(new ApiTokenFilter(tokens), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PATHS).hasAuthority(ApiTokenScope.READ_AUTHORITY)
                        .requestMatchers(HttpMethod.HEAD, PATHS).hasAuthority(ApiTokenScope.READ_AUTHORITY)
                        .anyRequest().hasAuthority(ApiTokenScope.WRITE_AUTHORITY))
                // The token travels in a header, which a foreign site cannot make a browser send. A browser with
                // cookies still needs the CSRF token for a change, so a cookie alone never counts.
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(ApiSecurityConfig::sentWithCookies))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(
                        new RequestAttributeSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> ApiProblems.write(response,
                                HttpStatus.UNAUTHORIZED, "Bitte einen gültigen API-Token senden."))
                        .accessDeniedHandler((request, response, e) -> ApiProblems.write(response,
                                HttpStatus.FORBIDDEN, FORBIDDEN)))
                .build();
    }

    /**
     * Tells whether a change comes with cookies, as from a browser. Only such a request needs the CSRF token.
     *
     * @param request the request
     * @return {@code true} for a change with cookies
     */
    static boolean sentWithCookies(HttpServletRequest request) {
        return !SAFE.contains(request.getMethod()) && request.getHeader(HttpHeaders.COOKIE) != null;
    }
}
