package io.github.grigoriev.im.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The size of an upload, checked before anything reads it. The CSRF check reads a form parameter, and the server then
 * writes every part of a multipart request to disk, before the access rules apply. So an anonymous client could fill
 * the disk with the 4 GB that the import of a full export may have. Only an admin sends that much, to the import.
 */
@Component
public class UploadLimit {

    /** A form upload: a document or receipt of at most 25 MB, with the other fields. */
    static final long FORM = 32L * 1024 * 1024;
    /** The import of a full export. Spring checks its 4 GB itself. */
    static final String IMPORT = "/admin/backup/import";

    /**
     * Returns the filter that refuses a multipart request over the limit, or without a declared length.
     *
     * @return the filter
     */
    public OncePerRequestFilter filter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                String type = request.getContentType();
                if (type != null && type.toLowerCase().startsWith("multipart/") && !allowed(request)) {
                    // No error page: its dispatch would pass the CSRF check again, which reads the upload.
                    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                    response.setHeader("Connection", "close");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }

    private static boolean allowed(HttpServletRequest request) {
        long length = request.getContentLengthLong();
        if (length < 0) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return length <= FORM || IMPORT.equals(path) && admin(SecurityContextHolder.getContext().getAuthentication());
    }

    private static boolean admin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
