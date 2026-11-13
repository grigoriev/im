package io.github.grigoriev.im.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the start page. */
@Controller
public class HomeController {

    /**
     * Shows the start page.
     *
     * @return the template name
     */
    @GetMapping("/")
    public String home() {
        return "home";
    }
}
