package com.sheout.auth.internal.security;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates the access token returned from OTP verification.
 * <p>
 * ASSUMPTION FLAGGED: no session/token strategy was specified, so this
 * implements the simplest standard fit for a stateless API called from two
 * PWAs - a single signed JWT (HS256), no refresh token, expiry configurable
 * via SHEOUT_AUTH_JWT_EXPIRY_MINUTES. Revocation before expiry (e.g. on
 * logout) is not implemented - not specified, and would need a
 * denylist/Redis check on every request to do properly. Full Spring
 * Security was deliberately not pulled in for this - it would add a lot of
 * machinery (filter chains, UserDetailsService, ...) beyond what a single
 * bearer-token check needs right now.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final Duration expiry;

    public JwtService(@Value("${sheout.auth.jwt-secret}") String secret,
                       @Value("${sheout.auth.jwt-expiry-minutes:43200}") long expiryMinutes) {
        // HS256 needs a key of at least 256 bits (32 bytes) - a short/weak
        // JWT_SECRET will fail fast here rather than silently producing an
        // insecure token.
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiry = Duration.ofMinutes(expiryMinutes);
    }

    public String issue(UUID accountId, AccountRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(accountId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    public Optional<CurrentAccount> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID accountId = UUID.fromString(claims.getSubject());
            AccountRole role = AccountRole.valueOf(claims.get("role", String.class));
            return Optional.of(new CurrentAccount(accountId, role));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
