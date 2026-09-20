package com.sheout.auth.internal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.auth.SessionRevocation;
import com.sheout.auth.internal.SessionService;
import com.sheout.sharedkernel.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs on every request. A valid, unexpired bearer token whose session is
 * still live populates {@link CurrentAccountContext} for the duration of the
 * request; anything else leaves the context empty and lets the request
 * continue, so each endpoint answers as it would for a signed-out caller
 * (OTP request/verify must work unauthenticated; admin endpoints require
 * ADMIN).
 * <p>
 * THE ONE CASE THIS FILTER ANSWERS ITSELF. A token whose session has been
 * revoked is refused here, with 401 SESSION_ENDED and the reason. Letting it
 * fall through as "signed out" would be true but useless: the app would show
 * its ordinary signed-out screen, and a partner signed out by her own second
 * phone, or somebody blocked mid-trip, would be left guessing. Knowing which
 * of her sessions ended tells her nothing she does not already have - she is
 * holding the token it was issued for.
 * <p>
 * A signature alone was never enough - a deleted account's token was already
 * refused - and it is now even less: the session lookup is what makes a block
 * take effect immediately rather than whenever the token happens to expire.
 * It costs one lookup by primary key, the same number of queries this filter
 * made before sessions existed. See SessionService.
 */
@Component
public class JwtAuthenticationFilter implements jakarta.servlet.Filter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionService sessions;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, SessionService sessions, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            Optional<CurrentAccount> caller = extractToken((HttpServletRequest) request).flatMap(jwtService::parse);
            if (caller.isPresent()) {
                Optional<SessionRevocation> ended = sessions.checkLive(caller.get().sessionId());
                if (ended.isPresent()) {
                    refuse((HttpServletRequest) request, (HttpServletResponse) response, ended.get());
                    return;
                }
                CurrentAccountContext.set(caller.get());
            }
            chain.doFilter(request, response);
        } finally {
            CurrentAccountContext.clear();
        }
    }

    /** 401 with the reason, so the app can say what happened rather than just showing a sign-in screen. */
    private void refuse(HttpServletRequest request, HttpServletResponse response, SessionRevocation reason)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "SESSION_ENDED",
                messageFor(reason),
                request.getRequestURI());
        // The reason travels as a detail so the apps can match on it without
        // reading the sentence, which is translated.
        ApiErrorResponse withReason = new ApiErrorResponse(
                body.timestamp(), body.status(), body.error(), body.message(), body.path(),
                java.util.List.of("reason: " + reason.name()));
        objectMapper.writeValue(response.getOutputStream(), withReason);
    }

    private String messageFor(SessionRevocation reason) {
        return switch (reason) {
            case SIGNED_IN_ELSEWHERE -> "You've been signed in on another device.";
            case ACCOUNT_BLOCKED -> "This account has been blocked by SheOut. Please contact support if you think this is a mistake.";
            case ACCOUNT_DELETED -> "This account has been deleted.";
            case SIGNED_OUT -> "You've been signed out.";
        };
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}
