package io.github.grigoriev.im.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the login page. Spring Security handles the form post and the logout. */
@Controller
public class LoginController {

    /**
     * Shows the login page.
     *
     * @return the template name
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
