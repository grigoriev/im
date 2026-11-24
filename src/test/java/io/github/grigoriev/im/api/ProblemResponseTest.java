package io.github.grigoriev.im.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletResponse;

class ProblemResponseTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            404 | 404 | Diese Adresse gibt es nicht.
            405 | 405 | Diese Methode gibt es unter dieser Adresse nicht.
            406 | 406 | Die Schnittstelle antwortet nur mit JSON.
            415 | 415 | Die Schnittstelle nimmt nur JSON an.
            400 | 400 | Die Anfrage ist ungültig.
            499 | 500 | Die Anfrage ist ungültig.
            500 | 500 | Ein Fehler im Server. Das Protokoll nennt ihn.
            503 | 503 | Ein Fehler im Server. Das Protokoll nennt ihn.
            """)
    void answersAnErrorAsProblemDetails(int status, int answered, String detail) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ProblemResponse(response).sendError(status);
        assertThat(response.getStatus()).isEqualTo(answered);
        assertThat(response.getErrorMessage()).as("no error page").isNull();
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"detail\":\"" + detail + "\"");
    }

    @ParameterizedTest
    @CsvSource({"404", "500"})
    void ignoresTheMessageOfTheServlet(int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ProblemResponse(response).sendError(status, "intern");
        assertThat(response.getContentAsString()).doesNotContain("intern");
    }
}
