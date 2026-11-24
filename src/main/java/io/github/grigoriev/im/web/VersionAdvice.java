package io.github.grigoriev.im.web;

import io.github.grigoriev.im.system.AppVersion;
import io.github.grigoriev.im.system.SystemService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Puts the version of the app into every page model, for the line below the menu. */
@ControllerAdvice(basePackageClasses = VersionAdvice.class)
public class VersionAdvice {

    private final SystemService system;

    /**
     * Creates the advice.
     *
     * @param system the system
     */
    public VersionAdvice(SystemService system) {
        this.system = system;
    }

    /**
     * Returns the version of the app.
     *
     * @return the version
     */
    @ModelAttribute("appVersion")
    public AppVersion appVersion() {
        return system.version();
    }
}
