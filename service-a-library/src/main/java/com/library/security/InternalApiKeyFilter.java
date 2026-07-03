package com.library.security;

import com.library.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates service-to-service calls under {@code /internal/**} using a shared
 * secret header instead of JWT: Service B has no end user or JWT of its own when it
 * calls back into Service A to validate a book, so a static API key is a pragmatic
 * choice here rather than standing up OAuth2 client-credentials for a single call.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final AppProperties appProperties;

    public InternalApiKeyFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey != null && providedKey.equals(appProperties.getInternal().getApiKey())) {
            var authToken = new UsernamePasswordAuthenticationToken(
                    "service-b", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
