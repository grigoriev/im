package io.github.grigoriev.im.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserTestData;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiTokenFilterTest {

    private static final Instant AT = Instant.parse("2027-03-18T08:12:00Z");

    private final ApiTokenService tokens = mock(ApiTokenService.class);
    private final ApiTokenFilter filter = new ApiTokenFilter(tokens);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearTheLogin() {
        SecurityContextHolder.clearContext();
    }

    private static ApiTokenLogin login(ApiTokenScope scope, Role role) {
        return new ApiTokenLogin(UserTestData.user(2L, "elena", role),
                new ApiToken(7L, 2L, "Claude", "h", scope, AT, AT.plusSeconds(60), null));
    }

    private MockHttpServletResponse send(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic abc", "Bearer", "Token im_x"})
    void refusesARequestWithoutABearerToken(String authorization) throws Exception {
        MockHttpServletResponse response = send(authorization);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        verify(chain, never()).doFilter(any(), any());
        verify(tokens, never()).authenticate(any());
    }

    @Test
    void refusesAnUploadBeforeItIsRead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/customers");
        request.setContentType("Multipart/Form-Data; boundary=x");
        request.addHeader("Authorization", "Bearer im_gut");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"detail\":\"Die Schnittstelle nimmt nur JSON an.\"");
        verify(chain, never()).doFilter(any(), any());
        verify(tokens, never()).authenticate(any());
    }

    @Test
    void letsJsonThrough() throws Exception {
        when(tokens.authenticate("im_gut")).thenReturn(Optional.of(login(ApiTokenScope.WRITE, Role.USER)));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/customers");
        request.setContentType("application/json");
        request.addHeader("Authorization", "Bearer im_gut");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void refusesARequestWithoutHeaderOrWithAnUnknownToken() throws Exception {
        MockHttpServletResponse anonymous = send(null);
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(anonymous.getContentAsString()).isEqualTo("{\"status\":401,\"title\":\"Unauthorized\","
                + "\"detail\":\"Bitte einen gültigen API-Token senden.\"}");
        assertThat(anonymous.getErrorMessage()).as("no error page").isNull();
        when(tokens.authenticate("im_falsch")).thenReturn(Optional.empty());
        assertThat(send("Bearer im_falsch").getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void logsAReadingTokenInWithTheRoleOfItsUser() throws Exception {
        when(tokens.authenticate("im_gut")).thenReturn(Optional.of(login(ApiTokenScope.READ, Role.USER)));
        MockHttpServletResponse response = send("bearer  im_gut ");
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo("elena");
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER", "SCOPE_READ");
        assertThat(authentication.getDetails()).isEqualTo(new ApiTokenDetails(7L, "Claude"));
    }

    @Test
    void aWritingTokenGetsTheWriteScope() {
        assertThat(ApiTokenFilter.authentication(login(ApiTokenScope.WRITE, Role.ADMIN)).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN", "SCOPE_READ", "SCOPE_WRITE");
    }
}
