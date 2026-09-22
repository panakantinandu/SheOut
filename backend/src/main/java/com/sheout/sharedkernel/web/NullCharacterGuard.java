package com.sheout.sharedkernel.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Keeps the NUL character (U+0000) out of the database.
 * <p>
 * PostgreSQL cannot store it in a text column and refuses the whole write.
 * One \u0000 in a support ticket, a chat message, a name or an address made
 * the request fail as a 500 - and as a logged server fault, so anybody could
 * fill the error log with them. It is never part of anything a person types.
 * <p>
 * In a JSON body it is removed as the string is read, so the rest of what
 * she wrote is saved. In a URL it is refused with a 400, since a query
 * parameter with a NUL in it is never a real search.
 */
@Configuration
public class NullCharacterGuard {

    @Bean
    Module stripNullCharacters() {
        SimpleModule module = new SimpleModule("strip-null-characters");
        module.addDeserializer(String.class, new StringDeserializer() {
            @Override
            public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                String value = super.deserialize(parser, context);
                return value == null || value.indexOf('\u0000') < 0 ? value : value.replace("\u0000", "");
            }
        });
        return module;
    }

    @Component
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class RejectNullInUrl extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (containsNull(request)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":400,\"error\":\"Bad Request\",\"message\":\"The address contains a character that is not allowed\"}");
                return;
            }
            chain.doFilter(request, response);
        }

        /**
         * The path and the query string only - never getParameter(), which on
         * a form-encoded POST reads the body, and the payment webhook has to
         * verify its signature over that body untouched.
         */
        private static boolean containsNull(HttpServletRequest request) {
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            return hasNull(uri) || (query != null && hasNull(query));
        }

        private static boolean hasNull(String raw) {
            return raw.indexOf('\u0000') >= 0 || raw.toLowerCase(java.util.Locale.ROOT).contains("%00");
        }
    }
}
