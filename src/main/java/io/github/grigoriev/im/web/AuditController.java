package io.github.grigoriev.im.web;

import io.github.grigoriev.im.audit.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** The page "Protokoll": who did what and when, for the admin only. */
@Controller
@RequestMapping("/admin/protokoll")
public class AuditController {

    static final String VIEW = "admin/audit";

    private final AuditService audit;

    /**
     * Creates the controller.
     *
     * @param audit the audit log
     */
    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    /**
     * Shows the newest entries.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("events", audit.latest());
        return VIEW;
    }
}
