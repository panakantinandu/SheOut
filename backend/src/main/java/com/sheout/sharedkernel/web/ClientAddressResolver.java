package com.sheout.sharedkernel.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The caller's IP address, for keying per-client rate limits.
 * <p>
 * On Render every request arrives from Render's own proxy, so
 * getRemoteAddr() is the same handful of internal addresses for everybody.
 * A per-IP limit keyed on that would be one global limit, and the first
 * attacker to hit it would lock out every other user.
 * <p>
 * The live service sits behind Cloudflare, which overwrites CF-Connecting-IP
 * with the address it actually accepted the connection from - a client
 * cannot choose it. CLIENT_IP_HEADER names that header in render.yaml.
 * X-Forwarded-For is deliberately not the default: Render appends to it
 * rather than replacing it, so its first entry is whatever the client sent.
 * <p>
 * Unset (local dev), or header absent on a request, falls back to the socket
 * address.
 */
@Component
public class ClientAddressResolver {

    private final String header;

    ClientAddressResolver(@Value("${sheout.rate-limit.client-ip-header:}") String header) {
        this.header = header == null ? "" : header.trim();
    }

    public String resolve(HttpServletRequest request) {
        if (!header.isEmpty()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // A single address is expected; take the first if a proxy
                // has joined several, and never trust more than 64 chars.
                String first = value.split(",")[0].trim();
                return first.length() > 64 ? first.substring(0, 64) : first;
            }
        }
        return request.getRemoteAddr();
    }
}
