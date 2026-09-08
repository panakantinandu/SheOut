package com.sheout.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Without this, every fetch() from a frontend app (a different origin -
 * localhost:5173/5174 vs the backend's localhost:8080) is rejected by the
 * browser's CORS check before it even reaches a controller - curl-based
 * testing never catches this since CORS is a browser-enforced restriction,
 * not a server-side one curl exercises. Found missing while wiring the
 * first real frontend screens to this backend.
 * <p>
 * allowCredentials is left false (the default) since auth uses a bearer
 * token set manually via the Authorization header, not cookies - fetch()
 * calls here never set credentials: 'include', so the stricter
 * "no wildcard origins with credentials" CORS rule doesn't apply.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(
            @Value("${sheout.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:5175}")
            String allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
