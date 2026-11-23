package io.github.grigoriev.im.web;

import io.github.grigoriev.im.system.SystemService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** The page "System": the version, the database schema, PostgreSQL and Java, for the admin only. */
@Controller
@RequestMapping("/admin/system")
public class SystemController {

    static final String VIEW = "admin/system";

    private final SystemService system;

    /**
     * Creates the controller.
     *
     * @param system the system
     */
    public SystemController(SystemService system) {
        this.system = system;
    }

    /**
     * Shows what runs.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("system", system.info());
        return VIEW;
    }
}
