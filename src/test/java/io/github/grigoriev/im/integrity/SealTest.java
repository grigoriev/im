package io.github.grigoriev.im.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class SealTest {

    private static final String KEY = "test-only-integrity-key-0123456789abcdef";
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private final Seal seal = new Seal(KEY);

    @Test
    void needsAKeyOfAtLeast32Characters() {
        assertThatThrownBy(() -> new Seal(null)).hasMessage("IM_INTEGRITY_KEY must have at least 32 characters");
        String short31 = "a".repeat(31);
        assertThatThrownBy(() -> new Seal(short31)).isInstanceOf(IllegalStateException.class);
        assertThat(new Seal("a".repeat(32)).businessDocument(1, ABC_SHA256)).hasSize(64);
    }

    @Test
    void sealsWithHmacSha256OverWhatItBelongsTo() {
        assertThat(seal.invoiceDocument(5, "PDF", ABC_SHA256))
                .isEqualTo(Seal.mac("HmacSHA256", new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"),
                        "invoice_document\n5\nPDF\n" + ABC_SHA256))
                .isNotEqualTo(seal.invoiceDocument(6, "PDF", ABC_SHA256))
                .isNotEqualTo(seal.invoiceDocument(5, "XML", ABC_SHA256))
                .isNotEqualTo(new Seal("b".repeat(32)).invoiceDocument(5, "PDF", ABC_SHA256));
        assertThat(seal.businessDocument(5, ABC_SHA256)).isEqualTo(Seal.mac("HmacSHA256",
                new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"), "business_document\n5\n"
                        + ABC_SHA256));
        assertThat(seal.invoiceOriginal(5, 9, ABC_SHA256)).isEqualTo(Seal.mac("HmacSHA256",
                new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"), "invoice_original\n5\n9\n"
                        + ABC_SHA256)).isNotEqualTo(seal.invoiceOriginal(6, 9, ABC_SHA256));
        assertThat(seal.snapshot(5, "2027-01", "abc")).isEqualTo(Seal.mac("HmacSHA256",
                new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"), "invoice\n5\n2027-01\n"
                        + ABC_SHA256));
        assertThat(Seal.mac("HmacSHA256", new SecretKeySpec("key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"),
                "The quick brown fox jumps over the lazy dog")).as("RFC test vector of Wikipedia")
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void comparesSeals() {
        String one = seal.businessDocument(1, ABC_SHA256);
        assertThat(Seal.matches(one, one)).isTrue();
        assertThat(Seal.matches(null, one)).isFalse();
        assertThat(Seal.matches(seal.businessDocument(2, ABC_SHA256), one)).isFalse();
    }

    @Test
    void hashesWithSha256() {
        assertThat(Seal.sha256("abc".getBytes(StandardCharsets.US_ASCII))).isEqualTo(ABC_SHA256);
        assertThatThrownBy(() -> Seal.digest("SHA-0", new byte[0])).isInstanceOf(IllegalStateException.class)
                .hasMessage("Java lacks SHA-0").hasCauseInstanceOf(NoSuchAlgorithmException.class);
        SecretKeySpec nope = new SecretKeySpec(new byte[] {1}, "HmacNope");
        assertThatThrownBy(() -> Seal.mac("HmacNope", nope, "x"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Java cannot compute HmacNope")
                .hasCauseInstanceOf(NoSuchAlgorithmException.class);
    }
}
