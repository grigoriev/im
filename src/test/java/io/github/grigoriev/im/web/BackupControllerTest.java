package io.github.grigoriev.im.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.backup.BackupException;
import io.github.grigoriev.im.backup.BackupService;
import io.github.grigoriev.im.config.SessionEpoch;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class BackupControllerTest {

    private final BackupService backups = mock(BackupService.class);
    private final SessionEpoch sessions = mock(SessionEpoch.class);
    private final BackupController controller = new BackupController(backups, sessions,
            Clock.fixed(Instant.parse("2027-03-18T08:00:00Z"), ZoneOffset.UTC));
    private final MultipartFile file = new MockMultipartFile("file", "export.zip", "application/zip", new byte[] {1});

    private static java.util.Map<String, Object> flashOf(RedirectAttributesModelMap flash) {
        return new java.util.HashMap<>(flash.getFlashAttributes());
    }

    @Test
    void showsThePageAndStreamsTheExport() throws IOException {
        assertThat(controller.show()).isEqualTo("settings/backup");
        var export = controller.export();
        assertThat(export.getHeaders().getContentDisposition().getFilename()).isEqualTo("im-datensicherung-2027-03-18.zip");
        assertThat(export.getHeaders().getContentType()).hasToString("application/zip");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        export.getBody().writeTo(out);
        verify(backups).export(out);
    }

    @Test
    void anImportEndsTheSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "x", "ROLE_ADMIN"));
        assertThat(controller.restore(file, request, new RedirectAttributesModelMap())).isEqualTo("redirect:/login?eingespielt");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(backups).restore(any(InputStream.class));
        verify(sessions).renew();
        assertThat(controller.restore(file, new MockHttpServletRequest(), new RedirectAttributesModelMap()))
                .as("no session to end").isEqualTo("redirect:/login?eingespielt");
    }

    @Test
    void aRefusedOrUnreadableImportStays() throws IOException {
        doThrow(new BackupException("Hier gibt es schon Daten.")).when(backups).restore(any(InputStream.class));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        assertThat(controller.restore(file, new MockHttpServletRequest(), flash)).isEqualTo("redirect:/admin/datensicherung");
        assertThat(flashOf(flash)).containsEntry("error", "Hier gibt es schon Daten.");

        RedirectAttributesModelMap none = new RedirectAttributesModelMap();
        assertThat(controller.restore(null, new MockHttpServletRequest(), none)).isEqualTo("redirect:/admin/datensicherung");
        assertThat(flashOf(none)).containsEntry("error", "Bitte eine ZIP-Datei wählen.");
        assertThat(controller.restore(new MockMultipartFile("file", new byte[0]), new MockHttpServletRequest(),
                new RedirectAttributesModelMap())).as("an empty file").isEqualTo("redirect:/admin/datensicherung");

        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getInputStream()).thenThrow(new IOException("gone"));
        RedirectAttributesModelMap unread = new RedirectAttributesModelMap();
        assertThat(controller.restore(broken, new MockHttpServletRequest(), unread)).isEqualTo("redirect:/admin/datensicherung");
        assertThat(flashOf(unread)).containsEntry("error", "Die Datei ließ sich nicht lesen.");
        verify(sessions, never()).renew();
    }
}
