package io.github.grigoriev.im.user;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.util.List;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The passkey store with an audit entry for each new passkey. Spring Security registers passkeys itself,
 * so this is the one place that sees a registration. Updates, such as the time of the last use, log nothing.
 */
public class AuditedCredentialRepository implements UserCredentialRepository {

    private final UserCredentialRepository delegate;
    private final AuditService audit;

    /**
     * Creates the store.
     *
     * @param delegate the store that keeps the passkeys
     * @param audit the audit log
     */
    public AuditedCredentialRepository(UserCredentialRepository delegate, AuditService audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    /** Saves the passkey and its audit entry in one transaction: Spring Security calls this without one. */
    @Override
    @Transactional
    public void save(CredentialRecord credential) {
        boolean added = delegate.findByCredentialId(credential.getCredentialId()) == null;
        delegate.save(credential);
        if (added) {
            audit.add(AuditAction.PASSKEY_ADDED, credential.getLabel());
        }
    }

    @Override
    public void delete(Bytes credentialId) {
        delegate.delete(credentialId);
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return delegate.findByCredentialId(credentialId);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return delegate.findByUserId(userId);
    }
}
