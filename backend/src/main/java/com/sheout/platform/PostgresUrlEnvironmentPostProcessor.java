package com.sheout.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reshapes DATABASE_URL from the standard postgres://user:pass@host:port/db
 * form - what Render, Neon, Supabase, and most managed Postgres providers
 * actually hand you - into the jdbc:postgresql://... form Spring's JDBC
 * driver requires. pgjdbc has no support at all for user:pass@ userinfo
 * syntax in the URL; credentials have to be query params instead
 * (?user=...&password=...).
 * <p>
 * This runs once, this early (Spring Boot's EnvironmentPostProcessor SPI,
 * registered in META-INF/spring.factories, executes before any bean -
 * including the DataSource - is created), so every profile downstream just
 * sees a working DATABASE_URL regardless of which provider issued it. This
 * replaces what used to be a manual, error-prone "reshape the string by
 * hand" step in the README for Neon - now automatic for Neon, Render's own
 * Postgres, Supabase, or a future AWS RDS instance alike, since they all
 * use the same standard URI form.
 * <p>
 * If DATABASE_URL is unset, already in jdbc: form, or doesn't parse as a
 * postgres(ql):// URI, this does nothing and leaves it untouched.
 * <p>
 * KNOWN LIMITATION: does not attempt to percent-decode a username/password
 * that itself contains a literal '@', ':', or '/' - fine for the
 * alphanumeric credentials every provider we've used generates, but flagged
 * rather than silently assumed correct for every possible password.
 */
public class PostgresUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        String reshaped = reshape(raw);
        if (reshaped == null) {
            return;
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("DATABASE_URL", reshaped);
        environment.getPropertySources().addFirst(new MapPropertySource("reshapedDatabaseUrl", overrides));
    }

    static String reshape(String raw) {
        if (raw == null || raw.isBlank() || raw.startsWith("jdbc:")) {
            return null;
        }
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("postgres") || scheme.equals("postgresql"))) {
                return null;
            }

            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath() == null ? "" : uri.getPath();

            StringBuilder params = new StringBuilder();
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] parts = userInfo.split(":", 2);
                params.append("user=").append(parts[0]);
                if (parts.length > 1) {
                    params.append("&password=").append(parts[1]);
                }
            }
            String query = uri.getQuery();
            if (query != null && !query.isBlank()) {
                if (params.length() > 0) {
                    params.append('&');
                }
                params.append(query);
            }

            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                    .append(host).append(':').append(port).append(path);
            if (params.length() > 0) {
                jdbc.append('?').append(params);
            }
            return jdbc.toString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}
