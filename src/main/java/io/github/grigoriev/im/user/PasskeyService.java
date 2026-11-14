package io.github.grigoriev.im.user;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists, renames and removes passkeys. Spring Security registers them and logs in with them.
 * A user changes only his or her own passkeys; the admin removes all of a user.
 */
@Service
public class PasskeyService {

    /** Maximum length of a passkey name. */
    static final int MAX_LABEL = 64;

    private final PublicKeyCredentialUserEntityRepository entities;
    private final UserCredentialRepository credentials;
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param entities the WebAuthn user entities
     * @param credentials the passkeys
     * @param audit the audit log
     */
    public PasskeyService(PublicKeyCredentialUserEntityRepository entities, UserCredentialRepository credentials,
            AuditService audit) {
        this.entities = entities;
        this.credentials = credentials;
        this.audit = audit;
    }

    /**
     * Lists the passkeys of a user, newest first.
     *
     * @param username the login name
     * @return the passkeys, empty if the user has none
     */
    public List<Passkey> list(String username) {
        return records(username).stream()
                .sorted(Comparator.comparing(CredentialRecord::getCreated, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(stored -> new Passkey(stored.getCredentialId().toBase64UrlString(), stored.getLabel(),
                        stored.getCreated(), stored.getLastUsed()))
                .toList();
    }

    /**
     * Counts the passkeys of a user.
     *
     * @param username the login name
     * @return the number of passkeys
     */
    public int count(String username) {
        return records(username).size();
    }

    /**
     * Renames a passkey of the user.
     *
     * @param username the login name of the owner
     * @param id the credential ID, Base64URL
     * @param label the new name
     * @throws UserException if the name is empty or too long, or the passkey is not the user's
     */
    @Transactional
    public void rename(String username, String id, String label) {
        if (label == null || label.isBlank()) {
            throw new UserException("Bitte einen Namen für den Passkey angeben.");
        }
        if (label.strip().length() > MAX_LABEL) {
            throw new UserException("Der Name hat höchstens " + MAX_LABEL + " Zeichen.");
        }
        CredentialRecord stored = owned(username, id);
        credentials.save(ImmutableCredentialRecord.fromCredentialRecord(stored).label(label.strip()).build());
        audit.add(username, AuditAction.PASSKEY_RENAMED, stored.getLabel() + " → " + label.strip());
    }

    /**
     * Removes a passkey of the user.
     *
     * @param username the login name of the owner
     * @param id the credential ID, Base64URL
     * @throws UserException if the passkey is not the user's
     */
    @Transactional
    public void remove(String username, String id) {
        CredentialRecord stored = owned(username, id);
        credentials.delete(stored.getCredentialId());
        audit.add(username, AuditAction.PASSKEY_REMOVED, stored.getLabel());
    }

    /**
     * Removes every passkey of a user, for example after a lost phone.
     *
     * @param username the login name
     * @return the number of removed passkeys
     */
    @Transactional
    public int removeAll(String username) {
        List<CredentialRecord> records = records(username);
        records.forEach(stored -> credentials.delete(stored.getCredentialId()));
        audit.add(AuditAction.PASSKEYS_REMOVED, username + ": " + records.size());
        return records.size();
    }

    private List<CredentialRecord> records(String username) {
        PublicKeyCredentialUserEntity entity = entities.findByUsername(username);
        return entity == null ? List.of() : credentials.findByUserId(entity.getId());
    }

    private CredentialRecord owned(String username, String id) {
        PublicKeyCredentialUserEntity entity = entities.findByUsername(username);
        CredentialRecord stored = credentialById(id);
        if (entity == null || stored == null || !stored.getUserEntityUserId().equals(entity.getId())) {
            throw new UserException("Diesen Passkey gibt es nicht.");
        }
        return stored;
    }

    private CredentialRecord credentialById(String id) {
        try {
            return credentials.findByCredentialId(Bytes.fromBase64(id));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
