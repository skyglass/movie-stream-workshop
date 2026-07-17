package skycomposer.moviechallenge.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects oversized request bodies before they reach Jackson/JPA, based on the Content-Length header alone
 * (cheap to check, avoids buffering/parsing a large payload just to find out it should be rejected).
 */
public class MaxRequestSizeFilter extends OncePerRequestFilter {
    private final long maxBytes;

    public MaxRequestSizeFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }
        chain.doFilter(request, response);
    }
}
