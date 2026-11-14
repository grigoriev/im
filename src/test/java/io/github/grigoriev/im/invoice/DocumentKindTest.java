package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DocumentKindTest {

    @Test
    void namesTheFilesAndTheirTypes() {
        assertThat(DocumentKind.PDF.filename("2027-01")).isEqualTo("Rechnung_2027-01.pdf");
        assertThat(DocumentKind.XML.filename("RE/27/1")).isEqualTo("Rechnung_RE-27-1.xml");
        assertThat(DocumentKind.PDF.mediaType()).isEqualTo("application/pdf");
        assertThat(DocumentKind.XML.mediaType()).isEqualTo("application/xml");
    }

    @Test
    void aStoredDocumentKeepsItsBytes() {
        byte[] bytes = {1, 2, 3};
        StoredDocument document = new StoredDocument(1L, 5L, DocumentKind.PDF, "a.pdf", bytes, "ab", 3,
                Instant.EPOCH);
        bytes[0] = 9;
        assertThat(document.content()).containsExactly(1, 2, 3);
        document.content()[1] = 9;
        assertThat(document.content()).containsExactly(1, 2, 3);
        assertThat(document).hasToString("StoredDocument[a.pdf, 3 bytes, sha256=ab]");
    }

    @Test
    void comparesStoredDocumentsByIdentityAndHash() {
        StoredDocument one = new StoredDocument(1L, 5L, DocumentKind.PDF, "a.pdf", new byte[] {1}, "ab", 1, Instant.EPOCH);
        StoredDocument same = new StoredDocument(1L, 5L, DocumentKind.PDF, "b.pdf", new byte[] {1}, "ab", 1, Instant.EPOCH);
        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo("a.pdf");
        assertThat(one.hashCode()).isEqualTo(java.util.Objects.hash(1L, 5L, DocumentKind.PDF, "ab"));
        assertThat(one).isNotEqualTo(new StoredDocument(2L, 5L, DocumentKind.PDF, "a.pdf", new byte[] {1}, "ab", 1,
                Instant.EPOCH));
        assertThat(one).isNotEqualTo(new StoredDocument(1L, 6L, DocumentKind.PDF, "a.pdf", new byte[] {1}, "ab", 1,
                Instant.EPOCH));
        assertThat(one).isNotEqualTo(new StoredDocument(1L, 5L, DocumentKind.XML, "a.pdf", new byte[] {1}, "ab", 1,
                Instant.EPOCH));
        assertThat(one).isNotEqualTo(new StoredDocument(1L, 5L, DocumentKind.PDF, "a.pdf", new byte[] {1}, "cd", 1,
                Instant.EPOCH));
    }
}
