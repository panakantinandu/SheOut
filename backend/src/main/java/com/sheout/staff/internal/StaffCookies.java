package com.sheout.staff.internal;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * The console's sign-in cookie.
 * <ul>
 *   <li>HttpOnly: no script on the page can read it, so a script injected
 *       into the console cannot carry the sign-in away. The token used to
 *       sit in localStorage, where any such script could.</li>
 *   <li>Secure: never sent over plain HTTP (browsers make an exception for
 *       localhost, which is how local development still works).</li>
 *   <li>SameSite=Strict: never sent on a request another site starts.</li>
 *   <li>Path=/api/v1/admin: sent to the console API and nowhere else - not to
 *       the apps' endpoints, not to the console page itself.</li>
 *   <li>No Max-Age: it goes when the browser closes. The server's own idle
 *       and absolute limits apply either way.</li>
 * </ul>
 */
public final class StaffCookies {

    public static final String NAME = "sheout_staff";
    public static final String PATH = "/api/v1/admin";

    private StaffCookies() {
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    static void set(HttpServletResponse response, String value, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, value)
                .httpOnly(true).secure(secure).sameSite("Strict").path(PATH).build().toString());
    }

    public static void clear(HttpServletResponse response, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, "")
                .httpOnly(true).secure(secure).sameSite("Strict").path(PATH).maxAge(0).build().toString());
    }
}
