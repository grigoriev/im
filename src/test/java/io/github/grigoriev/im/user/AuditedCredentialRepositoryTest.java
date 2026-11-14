package io.github.grigoriev.im.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;

class AuditedCredentialRepositoryTest {

    private static final Instant JAN = Instant.parse("2027-01-02T08:00:00Z");

    private final MapUserCredentialRepository store = new MapUserCredentialRepository();
    private final AuditService audit = mock(AuditService.class);
    private final AuditedCredentialRepository repository = new AuditedCredentialRepository(store, audit);

    @Test
    void recordsANewPasskeyButNotAnUpdate() {
        PublicKeyCredentialUserEntity elena = PasskeyTestData.entity(new MapPublicKeyCredentialUserEntityRepository(), "elena");
        CredentialRecord mac = PasskeyTestData.passkey(repository, elena, "MacBook Air", JAN, JAN);
        verify(audit).add(AuditAction.PASSKEY_ADDED, "MacBook Air");

        repository.save(ImmutableCredentialRecord.fromCredentialRecord(mac).lastUsed(JAN.plusSeconds(60)).build());
        verify(audit, never()).add(AuditAction.PASSKEY_ADDED, "iPhone");
        org.mockito.Mockito.verifyNoMoreInteractions(audit);

        assertThat(repository.findByCredentialId(mac.getCredentialId()).getLastUsed()).isEqualTo(JAN.plusSeconds(60));
        assertThat(repository.findByUserId(elena.getId())).hasSize(1);
        repository.delete(mac.getCredentialId());
        assertThat(repository.findByUserId(elena.getId())).isEmpty();
    }
}
