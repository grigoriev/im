package io.github.grigoriev.im.user;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

class LoginAuditTest {

    private final AuditService audit = mock(AuditService.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final LoginAudit listener = new LoginAudit(audit, users);

    @Test
    void recordsTheLoginMethod() {
        listener.succeeded(new AuthenticationSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated("elena", null, AuthorityUtils.NO_AUTHORITIES)));
        verify(audit).add("elena", AuditAction.LOGIN, "Passwort");
        listener.succeeded(new AuthenticationSuccessEvent(
                new RememberMeAuthenticationToken("key", "elena", AuthorityUtils.createAuthorityList("ROLE_USER"))));
        verify(audit).add("elena", AuditAction.LOGIN, "Angemeldet bleiben");
        var entity = ImmutablePublicKeyCredentialUserEntity.builder().id(Bytes.random()).name("elena").displayName("Elena").build();
        listener.succeeded(new AuthenticationSuccessEvent(
                new WebAuthnAuthentication(entity, AuthorityUtils.createAuthorityList("ROLE_USER"))));
        verify(audit).add("elena", AuditAction.LOGIN, "Passkey");
    }

    @Test
    void namesAKnownUserOfAFailedLoginButNeverTheEnteredText() {
        when(users.findByUsername("elena")).thenReturn(Optional.of(UserTestData.user(2, "elena", Role.USER)));
        listener.failed(new AuthenticationFailureBadCredentialsEvent(
                UsernamePasswordAuthenticationToken.unauthenticated("elena", "falsch"), new BadCredentialsException("x")));
        verify(audit).add("elena", AuditAction.LOGIN_FAILED, "falsches Passwort oder Passkey");
        listener.failed(new AuthenticationFailureBadCredentialsEvent(
                UsernamePasswordAuthenticationToken.unauthenticated("geheim-passwort-123", "x"), new BadCredentialsException("x")));
        verify(audit).add("unbekannt", AuditAction.LOGIN_FAILED, "unbekannter Benutzername");
    }
}
