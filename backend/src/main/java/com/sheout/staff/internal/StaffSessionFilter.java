package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.web.ApiErrorResponse;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Who on the staff is calling, for every request to the console API
 * (/api/v1/admin/**), from the console cookie.
 * <p>
 * THE CONSOLE NEVER RUNS AS A RIDER OR PARTNER. Whatever the bearer filter
 * found is cleared first: a rider's token sent to an admin endpoint is no
 * identity at all here, and a staff cookie's identity is the only one an
 * admin endpoint ever sees (in StaffContext, and as the staff account in
 * CurrentAccountContext for the code that records who decided something).
 * <p>
 * CHANGES NEED THE CSRF TOKEN. Every POST/PUT/PATCH/DELETE made with the
 * cookie must carry X-CSRF-Token matching the session's, and must not come
 * from another site (Sec-Fetch-Site). SameSite=Strict already keeps the
 * cookie off other sites' requests; this is the second lock on the same
 * door, and it also covers localhost, where the apps and the console count
 * as the same site.
 * <p>
 * This filter decides who; StaffPermissionInterceptor decides whether. A
 * cookie that has run out is not answered here, because signing in again
 * must still work with a stale cookie in the jar: the reason is left on the
 * request for the interceptor to give, if the endpoint needed a session.
 * <p>
 * ADMIN_PHONE_LOGIN_ENABLED (never in production) lets an old phone-and-code
 * ADMIN bearer token through as an OWNER, so local scripts keep working.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StaffSessionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    /** Sent by the console on its polls, so polling does not count as her being there. */
    static final String BACKGROUND_HEADER = "X-Staff-Background";
    static final String CSRF_HEADER = "X-CSRF-Token";

    /** Where the interceptor finds why a cookie did not get her in. */
    static final String ENDED_ATTRIBUTE = "sheout.staff.ended";

    private final StaffSessionService sessions;
    private final StaffSettings settings;
    private final com.sheout.sharedkernel.web.ClientAddressResolver clientAddress;
    private final ObjectMapper objectMapper;
    private final boolean legacyPhoneLogin;

    StaffSessionFilter(StaffSessionService sessions, StaffSettings settings, ObjectMapper objectMapper,
                       com.sheout.sharedkernel.web.ClientAddressResolver clientAddress,
                       @Value("${sheout.admin.phone-login-enabled:false}") boolean legacyPhoneLogin) {
        this.clientAddress = clientAddress;
        this.sessions = sessions;
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.legacyPhoneLogin = legacyPhoneLogin;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isConsoleApi(request);
    }

    static boolean isConsoleApi(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(StaffCookies.PATH) || path.startsWith(StaffCookies.PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<CurrentAccount> bearer = CurrentAccountContext.get();
        CurrentAccountContext.clear();
        try {
            String cookie = StaffCookies.read(request);
            if (cookie != null) {
                boolean activity = !"1".equals(request.getHeader(BACKGROUND_HEADER));
                StaffSessionService.Check result = sessions.authenticate(cookie, activity, clientAddress.resolve(request));
                if (result.principal() != null) {
                    if (!SAFE_METHODS.contains(request.getMethod())
                            && !trustedChange(request, result.session().getCsrfToken())) {
                        refuse(request, response);
                        return;
                    }
                    StaffContext.set(result.principal());
                    CurrentAccountContext.set(new CurrentAccount(
                            result.principal().accountId(), AccountRole.ADMIN, result.principal().sessionId()));
                } else {
                    request.setAttribute(ENDED_ATTRIBUTE, result.ended());
                    if (result.ended() != StaffSessionService.Ended.NOT_SIGNED_IN) {
                        StaffCookies.clear(response, settings.cookieSecure);
                    }
                }
            } else if (legacyPhoneLogin && bearer.isPresent() && bearer.get().role() == AccountRole.ADMIN) {
                StaffContext.set(new StaffPrincipal(null, bearer.get().accountId(), StaffRole.OWNER,
                        bearer.get().sessionId(), "Phone sign-in (legacy)", true));
                CurrentAccountContext.set(bearer.get());
            }
            chain.doFilter(request, response);
        } finally {
            StaffContext.clear();
            CurrentAccountContext.clear();
        }
    }

    private static boolean trustedChange(HttpServletRequest request, String csrfToken) {
        String site = request.getHeader("Sec-Fetch-Site");
        if (site != null && (site.equals("cross-site") || site.equals("same-site"))) {
            return false;
        }
        return StaffTokens.same(request.getHeader(CSRF_HEADER), csrfToken);
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(HttpStatus.FORBIDDEN.value(),
                "CSRF_TOKEN_INVALID", "This change did not come from the console page. Reload the console and try again.",
                request.getRequestURI()));
    }
}
