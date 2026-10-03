package com.sheout.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
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
                .allowedHeaders("*")
                // A browser hides every response header from a cross-origin
                // page except a short safelist, and neither of these is on it.
                // Without this the apps could not read the data export's file
                // name, or how long a 429 says to wait - both arrived, and
                // fetch() reported them as absent.
                .exposedHeaders("Content-Disposition", "Retry-After");
    }

    /**
     * The console at /admin/ as well as /admin/index.html. Invitation links
     * are /admin/#invite=..., and a link that opens a 404 is a link nobody
     * can use.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin", "/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }
}
