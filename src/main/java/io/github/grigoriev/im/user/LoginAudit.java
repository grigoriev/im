package io.github.grigoriev.im.user;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.stereotype.Component;

/**
 * Records the logins that Spring Security reports: who, and with which method. A failed login names the user
 * only if that user exists: a password typed into the name field never reaches the log.
 */
@Component
public class LoginAudit {

    private final AuditService audit;
    private final AppUserRepository users;

    /**
     * Creates the listener.
     *
     * @param audit the audit log
     * @param users the user accounts
     */
    public LoginAudit(AuditService audit, AppUserRepository users) {
        this.audit = audit;
        this.users = users;
    }

    /**
     * Records a successful login.
     *
     * @param event the event
     */
    @EventListener
    public void succeeded(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        audit.record(authentication.getName(), AuditAction.LOGIN, method(authentication));
    }

    /**
     * Records a failed login.
     *
     * @param event the event
     */
    @EventListener
    public void failed(AbstractAuthenticationFailureEvent event) {
        String name = event.getAuthentication().getName();
        boolean known = users.findByUsername(name).isPresent();
        audit.record(known ? name : "unbekannt", AuditAction.LOGIN_FAILED,
                known ? "falsches Passwort oder Passkey" : "unbekannter Benutzername");
    }

    static String method(Authentication authentication) {
        if (authentication instanceof WebAuthnAuthentication) {
            return "Passkey";
        }
        if (authentication instanceof RememberMeAuthenticationToken) {
            return "Angemeldet bleiben";
        }
        return "Passwort";
    }
}
