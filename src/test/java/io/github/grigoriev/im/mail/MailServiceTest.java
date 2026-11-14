package io.github.grigoriev.im.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.StoredDocument;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class MailServiceTest {

    private static final MailProperties ON = new MailProperties("smtp.example.org", 587, "elena", "geheim", true,
            "rechnung@elena.example", "");
    private static final LocalDate DAY = LocalDate.of(2027, 3, 18);

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final DispatchService dispatches = mock(DispatchService.class);
    private final DocumentStore documents = mock(DocumentStore.class);
    private final MailTextService texts = mock(MailTextService.class);

    private static Invoice invoice(InvoiceFormat format) {
        return new Invoice(5L, InvoiceStatus.ISSUED, 2L, "2027-07", format, DAY, DAY, DAY, 14, null, null, null, null,
                List.of(), Instant.EPOCH, Instant.EPOCH, 1L, Instant.EPOCH, "{}");
    }

    private static StoredDocument file(DocumentKind kind, byte[] content) {
        return new StoredDocument(1L, 5L, kind, kind.filename("2027-07"), content, "ab", content.length, Instant.EPOCH);
    }

    private static SendForm form(String to, String subject, String body, boolean pdf, boolean xml) {
        SendForm form = new SendForm();
        form.setTo(to);
        form.setSubject(subject);
        form.setBody(body);
        form.setPdf(pdf);
        form.setXml(xml);
        return form;
    }

    private MailService service(MailProperties properties) {
        return new MailService(properties, sender, dispatches, documents, texts);
    }

    @BeforeEach
    void stored() {
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        when(dispatches.sendable(5)).thenReturn(invoice(InvoiceFormat.ZUGFERD_EN16931));
        when(documents.find(5, DocumentKind.PDF)).thenReturn(Optional.of(file(DocumentKind.PDF, new byte[] {'%', 'P'})));
        when(documents.find(5, DocumentKind.XML)).thenReturn(Optional.of(file(DocumentKind.XML, "<x/>".getBytes())));
    }

    private MimeMessage sent() {
        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(message.capture());
        return message.getValue();
    }

    private static List<Part> parts(MimeMessage message) throws Exception {
        Multipart mixed = (Multipart) message.getContent();
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < mixed.getCount(); i++) {
            parts.add(mixed.getBodyPart(i));
        }
        return parts;
    }

    /** The text of a part; the helper nests it in a related multipart. */
    private static Object text(Part part) throws Exception {
        Object content = part.getContent();
        return content instanceof Multipart nested ? text(nested.getBodyPart(0)) : content;
    }

    @Test
    void sendsTheStoredDocumentsAndRecordsTheDispatch() throws Exception {
        service(ON).send(5, form(" buchhaltung@verlag.example ", " Rechnung 2027-07 ", "Hallo,\n\nanbei.\n", true, true));
        MimeMessage message = sent();
        message.saveChanges();
        assertThat(message.getFrom()[0]).hasToString("rechnung@elena.example");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0]).hasToString("buchhaltung@verlag.example");
        assertThat(message.getRecipients(Message.RecipientType.BCC)).isNull();
        assertThat(message.getSubject()).isEqualTo("Rechnung 2027-07");
        List<Part> parts = parts(message);
        assertThat(parts).extracting(Part::getFileName).containsExactly(null, "Rechnung_2027-07.pdf", "Rechnung_2027-07.xml");
        assertThat(text(parts.get(0))).as("the text, stripped").isEqualTo("Hallo,\n\nanbei.");
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        parts.get(1).getDataHandler().writeTo(pdf);
        assertThat(pdf.toByteArray()).containsExactly('%', 'P');
        assertThat(parts.get(1).getContentType()).startsWith("application/pdf");
        assertThat(parts.get(2).getContentType()).startsWith("application/xml");
        verify(dispatches).recordEmail(5, "buchhaltung@verlag.example", "PDF, XML");
    }

    @Test
    void sendsABlindCopyAndOneAttachment() throws Exception {
        MailService service = service(new MailProperties("smtp.example.org", 587, "", "", true, "rechnung@elena.example",
                "elena@elena.example"));
        service.send(5, form("a@b.example", "Rechnung", "Text", false, true));
        MimeMessage message = sent();
        assertThat(message.getRecipients(Message.RecipientType.BCC)[0]).hasToString("elena@elena.example");
        assertThat(parts(message)).extracting(Part::getFileName).containsExactly(null, "Rechnung_2027-07.xml");
        verify(dispatches).recordEmail(5, "a@b.example", "XML");
        verify(documents, never()).find(5, DocumentKind.PDF);
    }

    @Test
    void refusesWrongValues() {
        InvoiceException e = catchThrowableOfType(InvoiceException.class,
                () -> service(ON).send(5, form("keine-adresse", " ", " ", false, false)));
        assertThat(e.problems()).containsExactly("Bitte eine gültige E-Mail-Adresse angeben.", "Bitte den Betreff angeben.",
                "Bitte den Text angeben.", "Bitte PDF, XML oder beides anhängen.");
        assertThat(catchThrowableOfType(InvoiceException.class, () -> service(ON).send(5,
                form("a@b.example", "s".repeat(201), "b".repeat(4001), true, false))).problems())
                .containsExactly("Der Betreff hat höchstens 200 Zeichen.", "Der Text hat höchstens 4000 Zeichen.");
        assertThat(catchThrowableOfType(InvoiceException.class, () -> service(ON).send(5,
                form(null, "Betreff", null, true, false))).problems())
                .containsExactly("Bitte eine gültige E-Mail-Adresse angeben.", "Bitte den Text angeben.");
        when(documents.find(5, DocumentKind.XML)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(ON).send(5, form("a@b.example", "Betreff", "Text", false, true)))
                .hasMessage("Bitte PDF, XML oder beides anhängen.");
        verify(sender, never()).send(any(MimeMessage.class));
        verify(dispatches, never()).recordEmail(anyLong(), anyString(), anyString());
    }

    @Test
    void aFailedSendRecordsNothing() {
        doThrow(new MailSendException("connection refused")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service(ON).send(5, form("a@b.example", "Betreff", "Text", true, false)))
                .hasMessage(MailService.FAILED);
        verify(dispatches, never()).recordEmail(anyLong(), anyString(), anyString());
    }

    @Test
    void aSentMailWhoseRecordFailsSaysSo() {
        doThrow(new InvoiceException(List.of("Eine stornierte Rechnung wird nicht mehr versendet."))).when(dispatches)
                .recordEmail(5, "a@b.example", "PDF");
        assertThatThrownBy(() -> service(ON).send(5, form("a@b.example", "Betreff", "Text", true, false)))
                .hasMessage(MailService.NOT_RECORDED);
        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    void aBrokenAddressOfTheSenderRecordsNothing() {
        MailService service = service(new MailProperties("smtp.example.org", 587, "", "", true, "nicht gültig@@", ""));
        assertThatThrownBy(() -> service.send(5, form("a@b.example", "Betreff", "Text", true, false)))
                .hasMessage(MailService.FAILED);
        verify(dispatches, never()).recordEmail(anyLong(), anyString(), anyString());
    }

    @Test
    void sendsNothingWithoutAServer() {
        MailService service = service(new MailProperties("", 587, "", "", true, "", ""));
        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.send(5, form("a@b.example", "Betreff", "Text", true, false)))
                .hasMessage("Das Versenden per E-Mail ist nicht eingerichtet.");
        verifyNoInteractions(sender, dispatches);
    }

    @Test
    void fillsTheFormFromTheInvoice() {
        Invoice zugferd = invoice(InvoiceFormat.ZUGFERD_EN16931);
        when(texts.forInvoice(zugferd)).thenReturn(new MailText("Rechnung 2027-07", "Hallo"));
        SendForm form = service(ON).form(zugferd, "a@b.example");
        assertThat(form.getTo()).isEqualTo("a@b.example");
        assertThat(form.getSubject()).isEqualTo("Rechnung 2027-07");
        assertThat(form.getBody()).isEqualTo("Hallo");
        assertThat(form.isPdf()).isTrue();
        assertThat(form.isXml()).as("the PDF carries the XML").isFalse();
        Invoice xrechnung = invoice(InvoiceFormat.XRECHNUNG);
        when(texts.forInvoice(xrechnung)).thenReturn(new MailText("s", "b"));
        SendForm both = service(ON).form(xrechnung, null);
        assertThat(both.isPdf()).isTrue();
        assertThat(both.isXml()).isTrue();
        assertThat(service(ON).configured()).isTrue();
    }

    @Test
    void knowsWhenItIsSetUpAndHidesThePassword() {
        assertThat(new MailProperties("h", 25, "", "", false, "", "").configured()).isFalse();
        assertThat(new MailProperties(" ", 25, "", "", false, "a@b.example", "").configured()).isFalse();
        MailProperties properties = new MailProperties("smtp.example.org", 587, "elena", "geheim", true,
                "rechnung@elena.example", "");
        assertThat(properties).hasToString("MailProperties[host=smtp.example.org, port=587, username=elena, "
                + "from=rechnung@elena.example]");
    }
}
