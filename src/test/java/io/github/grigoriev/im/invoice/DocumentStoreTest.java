package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.IssuedEInvoice;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class DocumentStoreTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:00:00Z");
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private final StoredDocumentRepository repository = mock(StoredDocumentRepository.class);
    private final DocumentStore store = new DocumentStore(repository, JsonMapper.builder().findAndAddModules().build());

    @Test
    void hashesWithSha256() {
        assertThat(DocumentStore.sha256("abc".getBytes(StandardCharsets.US_ASCII))).isEqualTo(ABC_SHA256);
        assertThatThrownBy(() -> DocumentStore.digest("SHA-0", new byte[0])).isInstanceOf(IllegalStateException.class)
                .hasMessage("Java lacks SHA-0").hasCauseInstanceOf(java.security.NoSuchAlgorithmException.class);
    }

    @Test
    void storesThePdfAndTheXmlWithTheirHash() {
        byte[] abc = "abc".getBytes(StandardCharsets.US_ASCII);
        store.store(5, "RE/2027/10", new IssuedEInvoice(InvoiceFormat.XRECHNUNG, abc, new byte[] {1, 2}, List.of()), NOW);
        ArgumentCaptor<StoredDocument> saved = ArgumentCaptor.forClass(StoredDocument.class);
        verify(repository, times(2)).save(saved.capture());
        StoredDocument pdf = saved.getAllValues().get(0);
        assertThat(pdf.invoiceId()).isEqualTo(5L);
        assertThat(pdf.kind()).isEqualTo(DocumentKind.PDF);
        assertThat(pdf.filename()).isEqualTo("Rechnung_RE-2027-10.pdf");
        assertThat(pdf.content()).isEqualTo(abc);
        assertThat(pdf.sha256()).isEqualTo(ABC_SHA256);
        assertThat(pdf.size()).isEqualTo(3);
        assertThat(pdf.createdAt()).isEqualTo(NOW);
        StoredDocument xml = saved.getAllValues().get(1);
        assertThat(xml.kind()).isEqualTo(DocumentKind.XML);
        assertThat(xml.filename()).isEqualTo("Rechnung_RE-2027-10.xml");
        assertThat(xml.size()).isEqualTo(2);
    }

    @Test
    void listsAndFindsTheDocuments() {
        StoredDocument pdf = new StoredDocument(1L, 5L, DocumentKind.PDF, "a.pdf", new byte[] {1}, ABC_SHA256, 1, NOW);
        when(repository.findByInvoiceIdOrderByKindAsc(5L)).thenReturn(List.of(pdf));
        when(repository.findByInvoiceIdAndKind(5L, DocumentKind.PDF)).thenReturn(Optional.of(pdf));
        assertThat(store.list(5)).containsExactly(pdf);
        assertThat(store.find(5, DocumentKind.PDF)).contains(pdf);
        assertThat(store.find(6, DocumentKind.XML)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(InvoiceFormat.class)
    void restoresTheSnapshotAsItWasIssued(InvoiceFormat format) {
        InvoiceDocument document = SampleInvoices.sale(format);
        String json = store.snapshot(document);
        assertThat(json).contains("\"number\":\"2027-10\"").doesNotContain("\"total\"");
        InvoiceDocument restored = store.restore(json);
        assertThat(restored).isEqualTo(document);
        assertThat(restored.total()).isEqualByComparingTo("146.33");
    }
}
