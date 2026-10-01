package com.sheout.sharedkernel.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an id, so one action can be followed from a support
 * complaint to the log line and the audit row it left. A client may send its
 * own X-Request-Id (to tie a retry to its first attempt); anything that is
 * not a short plain token is replaced. The id goes back in the response
 * header and into the log context.
 * <p>
 * requestId() and userAgent() let code below the web layer - the trip audit
 * log - say which request and which device an action came from, without a
 * servlet type in its signature.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestInfo extends OncePerRequestFilter {

    private static final String ATTRIBUTE = "sheout.requestId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader("X-Request-Id");
        String id = given != null && SAFE.matcher(given).matches() ? given : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader("X-Request-Id", id);
        MDC.put("requestId", id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }

    public static Optional<String> requestId() {
        return current().map(r -> (String) r.getAttribute(ATTRIBUTE));
    }

    /** The calling device's User-Agent, cut to fit an audit column. */
    public static Optional<String> userAgent() {
        return current().map(r -> r.getHeader("User-Agent"))
                .map(ua -> ua.length() > 120 ? ua.substring(0, 120) : ua);
    }

    private static Optional<HttpServletRequest> current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? Optional.of(attrs.getRequest()) : Optional.empty();
    }
}
