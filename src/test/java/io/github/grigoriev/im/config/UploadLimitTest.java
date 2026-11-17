package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UploadLimitTest {

    private static final long FORM = UploadLimit.FORM;
    private static final long IMPORT = 4L * 1024 * 1024 * 1024;

    @AfterEach
    void clearTheLogin() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0} {1} bytes to {2} by {3}: {4}")
    @CsvSource(nullValues = "-", value = {
        "multipart/form-data; boundary=x, " + FORM + ", /expenses/new, -, 200",
        "multipart/form-data; boundary=x, " + (FORM + 1) + ", /expenses/new, ROLE_USER, 413",
        "MULTIPART/FORM-DATA; boundary=x, " + (FORM + 1) + ", /login, -, 413",
        "multipart/form-data; boundary=x, -1, /expenses/new, ROLE_USER, 413",
        "multipart/form-data; boundary=x, " + IMPORT + ", /admin/backup/import, ROLE_ADMIN, 200",
        "multipart/form-data; boundary=x, " + IMPORT + ", /admin/backup/import, ROLE_USER, 413",
        "multipart/form-data; boundary=x, " + IMPORT + ", /admin/backup/import, -, 413",
        "multipart/form-data; boundary=x, " + IMPORT + ", /admin/backup/importiert, ROLE_ADMIN, 413",
        "application/json, " + IMPORT + ", /webauthn/register, ROLE_USER, 200",
        "-, -1, /, -, 200",
    })
    void refusesAMultipartRequestOverItsLimit(String type, long length, String path, String role, int status)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path) {
            @Override
            public long getContentLengthLong() {
                return length;
            }
        };
        request.setContentType(type);
        if (role != null) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("elena", null, role));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new UploadLimit().filter().doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(chain.getRequest()).isSameAs(status == 200 ? request : null);
        assertThat(response.getHeader("Connection")).isEqualTo(status == 200 ? null : "close");
        assertThat(response.getErrorMessage()).as("no error page").isNull();
    }
}
