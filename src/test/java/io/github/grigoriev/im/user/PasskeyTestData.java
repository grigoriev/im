package io.github.grigoriev.im.user;

import java.time.Instant;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/** Builds WebAuthn user entities and passkeys as Spring Security stores them after a registration. */
public final class PasskeyTestData {

    private PasskeyTestData() {
    }

    /**
     * Saves a user entity.
     *
     * @param entities the repository
     * @param username the login name
     * @return the entity
     */
    public static PublicKeyCredentialUserEntity entity(PublicKeyCredentialUserEntityRepository entities, String username) {
        PublicKeyCredentialUserEntity entity = ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random()).name(username).displayName(username).build();
        entities.save(entity);
        return entity;
    }

    /**
     * Saves a passkey.
     *
     * @param credentials the repository
     * @param owner the user entity
     * @param label the name
     * @param created time of registration
     * @param lastUsed time of the last login, may be {@code null}
     * @return the passkey
     */
    public static CredentialRecord passkey(UserCredentialRepository credentials, PublicKeyCredentialUserEntity owner,
            String label, Instant created, Instant lastUsed) {
        CredentialRecord stored = ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(Bytes.random())
                .userEntityUserId(owner.getId())
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
                .signatureCount(0)
                .uvInitialized(true)
                .backupEligible(true)
                .backupState(true)
                .created(created)
                .lastUsed(lastUsed)
                .label(label)
                .build();
        credentials.save(stored);
        return stored;
    }
}
