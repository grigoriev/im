package io.github.grigoriev.im.integrity;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Seals what the app keeps with an HMAC-SHA256. The key lives outside the database, in {@code IM_INTEGRITY_KEY}: whoever
 * changes a file and its SHA-256 in the database cannot write a seal that matches. Each seal names what it belongs to,
 * so a document moved to another invoice does not match either.
 */
@Component
public class Seal {

    /** The shortest key: 32 characters, 256 bits of a random password. */
    static final int MIN_KEY = 32;
    static final String KEY_MISSING = "IM_INTEGRITY_KEY must have at least " + MIN_KEY + " characters";

    private static final String HMAC = "HmacSHA256";

    private final SecretKeySpec key;

    /**
     * Creates the seal.
     *
     * @param key the secret, at least 32 characters
     * @throws IllegalStateException if the key is shorter
     */
    public Seal(@Value("${im.integrity.key}") String key) {
        if (key == null || key.length() < MIN_KEY) {
            throw new IllegalStateException(KEY_MISSING);
        }
        this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC);
    }

    /**
     * Returns the seal of a document of an issued invoice.
     *
     * @param invoiceId the invoice
     * @param kind PDF or XML
     * @param sha256 the SHA-256 of the file
     * @return the seal, 64 hexadecimal characters
     */
    public String invoiceDocument(long invoiceId, String kind, String sha256) {
        return seal("invoice_document", Long.toString(invoiceId), kind, sha256);
    }

    /**
     * Returns the seal of the snapshot of an issued invoice.
     *
     * @param invoiceId the invoice
     * @param number its number
     * @param snapshot the JSON
     * @return the seal, 64 hexadecimal characters
     */
    public String snapshot(long invoiceId, String number, String snapshot) {
        return seal("invoice", Long.toString(invoiceId), number, sha256(snapshot.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Returns the seal of a business document.
     *
     * @param id the document
     * @param sha256 the SHA-256 of the file
     * @return the seal, 64 hexadecimal characters
     */
    public String businessDocument(long id, String sha256) {
        return seal("business_document", Long.toString(id), sha256);
    }

    /**
     * Compares a stored seal with the one it should be, in constant time.
     *
     * @param stored the stored seal, may be {@code null}
     * @param expected the seal computed now
     * @return whether they are the same
     */
    public static boolean matches(String stored, String expected) {
        return stored != null && MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Returns the SHA-256 of bytes.
     *
     * @param content the bytes
     * @return the hash, 64 hexadecimal characters
     */
    public static String sha256(byte[] content) {
        return digest("SHA-256", content);
    }

    /**
     * Returns the hash of bytes.
     *
     * @param algorithm the algorithm
     * @param content the bytes
     * @return the hash, hexadecimal
     * @throws IllegalStateException if Java lacks the algorithm; every Java has SHA-256
     */
    static String digest(String algorithm, byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Java lacks " + algorithm, e);
        }
    }

    private String seal(String... parts) {
        return mac(HMAC, key, String.join("\n", parts));
    }

    /**
     * Returns the MAC of a text.
     *
     * @param algorithm the algorithm
     * @param key the key
     * @param text the text, as UTF-8
     * @return the MAC, hexadecimal
     * @throws IllegalStateException if Java lacks the algorithm or refuses the key; every Java has HmacSHA256
     */
    static String mac(String algorithm, SecretKeySpec key, String text) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Java cannot compute " + algorithm, e);
        }
    }
}
