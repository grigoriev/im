package io.github.grigoriev.im.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/** Stores the passkeys in PostgreSQL. Without these beans Spring Security would keep them in memory. */
@Configuration(proxyBeanMethods = false)
public class PasskeyConfig {

    /**
     * Returns the WebAuthn user entities, one per user with a passkey.
     *
     * @param jdbc the database
     * @return the repository on table {@code user_entities}
     */
    @Bean
    public PublicKeyCredentialUserEntityRepository passkeyUsers(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    /**
     * Returns the passkeys.
     *
     * @param jdbc the database
     * @return the repository on table {@code user_credentials}
     */
    @Bean
    public UserCredentialRepository passkeyCredentials(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }
}
