package com.sheout.auth.internal.security;

import com.sheout.auth.CurrentAccountContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs on every request. If a valid, unexpired bearer token is present it
 * populates {@link CurrentAccountContext} for the duration of the request;
 * otherwise it leaves the context empty and lets the request continue -
 * this filter never itself rejects a request. Whether a missing/invalid
 * token is acceptable is each endpoint's own decision (OTP request/verify
 * must work unauthenticated; admin review endpoints require ADMIN).
 */
@Component
public class JwtAuthenticationFilter implements jakarta.servlet.Filter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            extractToken((HttpServletRequest) request)
                    .flatMap(jwtService::parse)
                    .ifPresent(CurrentAccountContext::set);
            chain.doFilter(request, response);
        } finally {
            CurrentAccountContext.clear();
        }
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}
