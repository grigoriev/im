package io.github.grigoriev.im.user;

import static io.github.grigoriev.im.user.PasskeyTestData.entity;
import static io.github.grigoriev.im.user.PasskeyTestData.passkey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;

class PasskeyServiceTest {

    private static final Instant JAN = Instant.parse("2027-01-02T09:00:00Z");
    private static final Instant MAR = Instant.parse("2027-03-15T08:00:00Z");

    private final MapPublicKeyCredentialUserEntityRepository entities = new MapPublicKeyCredentialUserEntityRepository();
    private final MapUserCredentialRepository credentials = new MapUserCredentialRepository();
    private final AuditService audit = mock(AuditService.class);
    private final PasskeyService service = new PasskeyService(entities, credentials, audit);

    private final PublicKeyCredentialUserEntity elena = entity(entities, "elena");
    private final PublicKeyCredentialUserEntity admin = entity(entities, "admin");

    @Test
    void listsThePasskeysOfTheUserNewestFirst() {
        CredentialRecord mac = passkey(credentials, elena, "MacBook Air", JAN, MAR);
        CredentialRecord phone = passkey(credentials, elena, "iPhone", MAR, null);
        passkey(credentials, admin, "Admin-Mac", JAN, null);
        assertThat(service.list("elena")).containsExactly(
                new Passkey(phone.getCredentialId().toBase64UrlString(), "iPhone", MAR, null),
                new Passkey(mac.getCredentialId().toBase64UrlString(), "MacBook Air", JAN, MAR));
        assertThat(service.count("elena")).isEqualTo(2);
    }

    @Test
    void knowsNoPasskeysOfAUserWithoutEntity() {
        assertThat(service.list("niemand")).isEmpty();
        assertThat(service.count("niemand")).isZero();
        assertThat(service.removeAll("niemand")).isZero();
    }

    @Test
    void sortsPasskeysWithoutCreationTimeLast() {
        passkey(credentials, elena, "Alt", null, null);
        passkey(credentials, elena, "Neu", MAR, null);
        assertThat(service.list("elena")).extracting(Passkey::label).containsExactly("Neu", "Alt");
    }

    @Test
    void renamesAPasskeyOfTheUser() {
        CredentialRecord mac = passkey(credentials, elena, "MacBook Air", JAN, null);
        service.rename("elena", mac.getCredentialId().toBase64UrlString(), "  Arbeits-Mac ");
        assertThat(credentials.findByCredentialId(mac.getCredentialId()).getLabel()).isEqualTo("Arbeits-Mac");
        verify(audit).record("elena", AuditAction.PASSKEY_RENAMED, "MacBook Air → Arbeits-Mac");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void requiresAName(String label) {
        CredentialRecord mac = passkey(credentials, elena, "MacBook Air", JAN, null);
        String id = mac.getCredentialId().toBase64UrlString();
        assertThatThrownBy(() -> service.rename("elena", id, label)).isInstanceOf(UserException.class)
                .hasMessage("Bitte einen Namen für den Passkey angeben.");
    }

    @Test
    void limitsTheNameTo64Characters() {
        CredentialRecord mac = passkey(credentials, elena, "MacBook Air", JAN, null);
        String id = mac.getCredentialId().toBase64UrlString();
        service.rename("elena", id, "x".repeat(64));
        String tooLong = "x".repeat(65);
        assertThatThrownBy(() -> service.rename("elena", id, tooLong)).isInstanceOf(UserException.class)
                .hasMessage("Der Name hat höchstens 64 Zeichen.");
    }

    @Test
    void neverTouchesThePasskeyOfAnotherUser() {
        CredentialRecord adminKey = passkey(credentials, admin, "Admin-Mac", JAN, null);
        String id = adminKey.getCredentialId().toBase64UrlString();
        assertThatThrownBy(() -> service.rename("elena", id, "Meins")).isInstanceOf(UserException.class)
                .hasMessage("Diesen Passkey gibt es nicht.");
        assertThatThrownBy(() -> service.remove("elena", id)).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> service.remove("niemand", id)).isInstanceOf(UserException.class);
        assertThat(credentials.findByCredentialId(adminKey.getCredentialId()).getLabel()).isEqualTo("Admin-Mac");
        org.mockito.Mockito.verifyNoInteractions(audit);
    }

    @Test
    void refusesAnUnknownOrBrokenId() {
        assertThatThrownBy(() -> service.remove("elena", "AAAA")).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> service.remove("elena", "%%%")).isInstanceOf(UserException.class)
                .hasMessage("Diesen Passkey gibt es nicht.");
    }

    @Test
    void removesOneOrAllPasskeysOfTheUser() {
        CredentialRecord mac = passkey(credentials, elena, "MacBook Air", JAN, null);
        passkey(credentials, elena, "iPhone", MAR, null);
        CredentialRecord adminKey = passkey(credentials, admin, "Admin-Mac", JAN, null);
        service.remove("elena", mac.getCredentialId().toBase64UrlString());
        assertThat(service.count("elena")).isEqualTo(1);
        assertThat(service.removeAll("elena")).isEqualTo(1);
        assertThat(service.count("elena")).isZero();
        assertThat(credentials.findByCredentialId(adminKey.getCredentialId())).isNotNull();
        verify(audit).record("elena", AuditAction.PASSKEY_REMOVED, "MacBook Air");
        verify(audit).record(AuditAction.PASSKEYS_REMOVED, "elena: 1");
    }
}
