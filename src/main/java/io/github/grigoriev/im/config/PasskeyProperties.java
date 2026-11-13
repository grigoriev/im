package io.github.grigoriev.im.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The relying party of the passkeys. A passkey belongs to its host name: set {@code IM_WEBAUTHN_RP_ID}
 * to the host of the app, for example {@code im.h.g7v.io}, and {@code IM_WEBAUTHN_ALLOWED_ORIGINS} to its
 * address, for example {@code https://im.h.g7v.io}.
 *
 * @param rpId host name the passkeys belong to
 * @param rpName name the browser shows when it creates a passkey
 * @param allowedOrigins addresses the browser may call the app from
 */
@ConfigurationProperties("im.webauthn")
public record PasskeyProperties(
        @DefaultValue("localhost") String rpId,
        @DefaultValue("Rechnungen") String rpName,
        @DefaultValue("http://localhost:8080") List<String> allowedOrigins) {
}
