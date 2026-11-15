package io.github.grigoriev.im.web;

import io.github.grigoriev.im.integrity.IntegrityMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import tools.jackson.core.JacksonException;

/**
 * Shows what is broken when stored data cannot be read, for example a snapshot changed outside the app into JSON that
 * no longer parses. Every page that reads it would fail; this page checks everything at once and names the damage.
 */
@ControllerAdvice
public class DamagedDataAdvice {

    static final String VIEW = "damaged";

    private static final Logger LOG = LoggerFactory.getLogger(DamagedDataAdvice.class);

    private final IntegrityMonitor integrity;

    /**
     * Creates the advice.
     *
     * @param integrity checks every document and snapshot
     */
    public DamagedDataAdvice(IntegrityMonitor integrity) {
        this.integrity = integrity;
    }

    /**
     * Checks everything and shows what failed.
     *
     * @param e the failure to read
     * @param model the page model
     * @return the template name
     */
    @ExceptionHandler(JacksonException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unreadable(JacksonException e, Model model) {
        LOG.error("Stored data cannot be read: {}", e.getOriginalMessage());
        model.addAttribute("damaged", integrity.check().damaged());
        return VIEW;
    }
}
