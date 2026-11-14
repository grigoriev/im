package io.github.grigoriev.im.config;

import io.github.grigoriev.im.user.AppUserDetailsService;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/**
 * Login and access rules (docs/PLAN.md, section 5): form login with a password, remember-me, CSRF
 * protection, the admin area for {@code ADMIN} only, and a strict content security policy.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** Every asset comes from the app itself (docs/PLAN.md, section 11). */
    static final String CSP = "default-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    static final Duration REMEMBER_ME = Duration.ofDays(30);

    /**
     * Returns the access rules.
     *
     * @param http the security builder
     * @param tokens store of the remember-me tokens
     * @param users loads the users
     * @param rememberMeKey key that signs the remember-me cookie
     * @param staleForms handles forms that outlived their session
     * @param passkeys the relying party of the passkeys
     * @return the filter chain
     * @throws Exception if the configuration is invalid
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, PersistentTokenRepository tokens,
            AppUserDetailsService users, @Value("${im.remember-me.key:im-remember-me}") String rememberMeKey,
            StaleFormHandler staleForms, PasskeyProperties passkeys, SessionEpoch epoch) throws Exception {
        // The passkey login looks the users up here, even if the context holds another user store.
        http.setSharedObject(UserDetailsService.class, users);
        return http
                .addFilterBefore(epoch.filter(), SecurityContextHolderFilter.class)
                .userDetailsService(users)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/vendor/**", "/fonts/**", "/error",
                                "/actuator/health", "/actuator/health/**", "/webauthn/authenticate/options",
                                "/login/webauthn").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .webAuthn(webAuthn -> webAuthn
                        .rpName(passkeys.rpName())
                        .rpId(passkeys.rpId())
                        .allowedOrigins(Set.copyOf(passkeys.allowedOrigins()))
                        .disableDefaultRegistrationPage(true))
                .logout(logout -> logout.logoutSuccessUrl("/login?abgemeldet").deleteCookies("JSESSIONID"))
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .tokenRepository(tokens)
                        .userDetailsService(users)
                        .rememberMeParameter("angemeldet-bleiben")
                        .rememberMeCookieName(StaleFormHandler.REMEMBER_ME_COOKIE)
                        .tokenValiditySeconds((int) REMEMBER_ME.toSeconds()))
                .exceptionHandling(errors -> errors.accessDeniedHandler(staleForms))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CSP)))
                .build();
    }

    /**
     * Returns the password encoder: Argon2 for new hashes, the id prefix keeps old hashes readable.
     *
     * @return the encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("argon2",
                Map.of("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    /**
     * Returns the store of the remember-me tokens, table {@code persistent_logins}.
     *
     * @param dataSource the database
     * @return the token store
     */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl tokens = new JdbcTokenRepositoryImpl();
        tokens.setDataSource(dataSource);
        return tokens;
    }
}
