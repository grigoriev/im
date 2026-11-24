package io.github.grigoriev.im.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.http.HttpStatus;

/**
 * Turns an error that the servlet answers with {@code sendError}, such as an unknown URL or a body that does not parse,
 * into problem details. The error page would answer in HTML, through the chain of the pages.
 */
class ProblemResponse extends HttpServletResponseWrapper {

    /**
     * Wraps a response.
     *
     * @param response the response
     */
    ProblemResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void sendError(int status) throws IOException {
        sendError(status, null);
    }

    @Override
    public void sendError(int status, String message) throws IOException {
        HttpStatus known = HttpStatus.resolve(status);
        ApiProblems.write((HttpServletResponse) getResponse(), known == null ? HttpStatus.INTERNAL_SERVER_ERROR : known,
                detail(status));
    }

    /**
     * Returns the German detail of an error.
     *
     * @param status the status
     * @return what went wrong
     */
    static String detail(int status) {
        return switch (status) {
            case 404 -> "Diese Adresse gibt es nicht.";
            case 405 -> "Diese Methode gibt es unter dieser Adresse nicht.";
            case 406 -> "Die Schnittstelle antwortet nur mit JSON.";
            case 415 -> "Die Schnittstelle nimmt nur JSON an.";
            default -> status >= 500 ? "Ein Fehler im Server. Das Protokoll nennt ihn." : "Die Anfrage ist ungültig.";
        };
    }
}
