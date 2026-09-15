package com.sheout.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Response security headers, and nothing else.
 * <p>
 * Spring Security is on the classpath for its header writers, not for
 * authentication. Who is calling is still JwtAuthenticationFilter's job and
 * whether they may is still each controller's (see CurrentAccountContext and
 * the enumeration-safe 404s) - so every request is permitted here, and CSRF,
 * sessions, form login and HTTP Basic are all off. The API takes a bearer
 * token in a header, never a cookie, which is what makes turning CSRF off
 * correct rather than convenient.
 * <p>
 * Two Content-Security-Policies, because this server serves two kinds of
 * thing:
 * <ul>
 *   <li>JSON from /api. Nothing should ever render it as a page, so its
 *       policy allows nothing at all.</li>
 *   <li>The ops console at /admin, one static page with one inline script
 *       and one inline style. Rather than 'unsafe-inline' - which would let
 *       any injected script run on the one page that holds an admin token -
 *       the policy allows exactly those two blocks by SHA-256 hash, computed
 *       from the file at startup so an edit to the page cannot silently
 *       break or bypass it. A new inline script, or an inline event handler,
 *       will be blocked; put it in the existing script block.</li>
 * </ul>
 * The two frontends are served by Vercel, not here, so these headers never
 * reach their pages; they only govern the API responses the apps fetch.
 * <p>
 * HSTS is written on every response, not only ones Spring believes arrived
 * over HTTPS. TLS ends at Cloudflare/Render and the app sees plain HTTP, so
 * the default would never send it. Browsers ignore HSTS received over plain
 * HTTP, which is what keeps this harmless on localhost.
 */
@Configuration
public class SecurityHeadersConfig {

    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    private static final String ADMIN_PAGE = "static/admin/index.html";

    @Bean
    SecurityFilterChain securityHeaders(HttpSecurity http) throws Exception {
        RequestMatcher admin = new AntPathRequestMatcher("/admin/**");
        // Signed photo and document links set their own private, time-limited
        // Cache-Control. The default no-store written in front of it (headers
        // are written eagerly, see below) made browsers re-download every
        // photo on every screen.
        RequestMatcher signedDocuments = new AntPathRequestMatcher("/api/v1/documents");

        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Uses WebConfig's CORS mappings, so a preflight is answered
                // before anything else in the chain looks at it.
                .cors(Customizer.withDefaults())
                .headers(headers -> {
                    // Eager, not the default lazy writing. CVE-2026-22732:
                    // Spring Security 6.3.0-6.3.14 can fail to write headers
                    // when writing lazily, and the fixed 6.x lines need a
                    // Spring Boot upgrade this app has not had yet. Every
                    // header here is static, so writing before the request
                    // is handled costs nothing. Drop this once on a fixed
                    // version.
                    headers.addObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                        @Override
                        public <O extends HeaderWriterFilter> O postProcess(O filter) {
                            filter.setShouldWriteHeadersEagerly(true);
                            return filter;
                        }
                    });
                    headers
                            .cacheControl(cache -> cache.disable())
                            .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                    new NegatedRequestMatcher(signedDocuments), new CacheControlHeadersWriter()))
                            .contentTypeOptions(Customizer.withDefaults())
                            .frameOptions(frame -> frame.deny())
                            .httpStrictTransportSecurity(hsts -> hsts
                                    .requestMatcher(AnyRequestMatcher.INSTANCE)
                                    .includeSubDomains(true)
                                    .maxAgeInSeconds(31_536_000))
                            .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                    admin, new StaticHeadersWriter("Content-Security-Policy", adminCsp())))
                            .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                    new NegatedRequestMatcher(admin),
                                    new StaticHeadersWriter("Content-Security-Policy", API_CSP)));
                });
        return http.build();
    }

    /**
     * Firebase's messaging SDK, at one pinned version. The path, not just the
     * host, is what is allowed: nothing else Google serves from gstatic can
     * run on the page that holds an operator's token. Bump together with the
     * version in the console's FIREBASE_BASE.
     */
    static final String FIREBASE_SCRIPTS = "https://www.gstatic.com/firebasejs/12.19.0/";

    /** The two endpoints Firebase calls to issue and register a push token. */
    static final String FIREBASE_CONNECT = "https://firebaseinstallations.googleapis.com https://fcmregistrations.googleapis.com";

    /**
     * img-src allows https: because a document under review is opened from
     * wherever storage presigns it (an S3 host this code does not know in
     * advance). Apart from Firebase's two token endpoints, everything the
     * page fetches is same-origin; the push service worker is too.
     */
    static String adminCsp() {
        String html = readAdminPage();
        String scripts = hashes(html, "script");
        String styles = hashes(html, "style");
        return "default-src 'none'"
                + "; script-src " + scripts + " " + FIREBASE_SCRIPTS
                + "; style-src " + styles
                + "; img-src 'self' https: data: blob:"
                + "; connect-src 'self' " + FIREBASE_CONNECT
                + "; worker-src 'self'"
                + "; frame-ancestors 'none'"
                + "; base-uri 'none'"
                + "; form-action 'self'";
    }

    private static String readAdminPage() {
        try (InputStream in = new ClassPathResource(ADMIN_PAGE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Fail startup rather than serve the console with a policy that
            // either blocks it entirely or was never computed.
            throw new IllegalStateException("Cannot read " + ADMIN_PAGE + " to build its Content-Security-Policy", e);
        }
    }

    /** 'sha256-...' for every attribute-less inline block of this tag. */
    private static String hashes(String html, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL).matcher(html);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add("'sha256-" + sha256(m.group(1)) + "'");
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("No inline <" + tag + "> found in " + ADMIN_PAGE);
        }
        return String.join(" ", out);
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
