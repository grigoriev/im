package io.github.grigoriev.im.web;

import io.github.grigoriev.im.backup.BackupException;
import io.github.grigoriev.im.backup.BackupRunService;
import io.github.grigoriev.im.backup.BackupService;
import io.github.grigoriev.im.config.SessionEpoch;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The admin page "Datensicherung" of the settings: the status of the automatic backup, the full export and its import
 * into an empty installation.
 */
@Controller
@RequestMapping("/admin/datensicherung")
public class BackupController {

    static final String VIEW = "settings/backup";
    private static final String BACK = "redirect:/admin/datensicherung";
    private static final String ERROR = "error";

    private final BackupService backups;
    private final BackupRunService runs;
    private final SessionEpoch sessions;
    private final Clock clock;

    /**
     * Creates the controller.
     *
     * @param backups writes and reads the export
     * @param runs the last automatic backup and restore test
     * @param sessions ends every session after an import
     * @param clock the date in the file name
     */
    public BackupController(BackupService backups, BackupRunService runs, SessionEpoch sessions, Clock clock) {
        this.backups = backups;
        this.runs = runs;
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * Shows the page.
     *
     * @param model receives the status of the automatic backup
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("status", runs.status());
        return VIEW;
    }

    /**
     * Downloads the full export.
     *
     * @return the ZIP, written while it goes out
     */
    @GetMapping("/export.zip")
    public ResponseEntity<StreamingResponseBody> export() {
        StreamingResponseBody body = backups::export;
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("im-datensicherung-" + LocalDate.now(clock) + ".zip").build().toString())
                .body(body);
    }

    /**
     * Reads an export into this empty installation. The users of the export replace the ones here, so every session
     * ends and the login page asks for their access.
     *
     * @param file the uploaded ZIP, {@code null} without one
     * @param request the request, to end the session
     * @param flash messages for the next page
     * @return a redirect to the login page, or back to this page with the problem
     */
    @PostMapping("/import")
    public String restore(@RequestParam(required = false) MultipartFile file, HttpServletRequest request,
            RedirectAttributes flash) {
        if (file == null || file.isEmpty()) {
            flash.addFlashAttribute(ERROR, "Bitte eine ZIP-Datei wählen.");
            return BACK;
        }
        try (InputStream in = file.getInputStream()) {
            backups.restore(in);
        } catch (BackupException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
            return BACK;
        } catch (IOException _) {
            flash.addFlashAttribute(ERROR, "Die Datei ließ sich nicht lesen.");
            return BACK;
        }
        // The users of the export replace the ones here: every session ends, in every browser.
        sessions.renew();
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?eingespielt";
    }
}
