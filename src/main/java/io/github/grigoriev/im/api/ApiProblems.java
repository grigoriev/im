package io.github.grigoriev.im.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes a refusal of the filter chain as RFC 9457 problem details. It answers directly, without the error page: its
 * dispatch would run the chain of the pages, with its session and "Angemeldet bleiben".
 */
final class ApiProblems {

    private ApiProblems() {
    }

    /**
     * Writes a problem.
     *
     * @param response the response
     * @param status the status
     * @param detail what is wrong, in German, without quotes or backslashes
     * @throws IOException if the response cannot be written
     */
    static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase()
                + "\",\"detail\":\"" + detail + "\"}");
    }
}
