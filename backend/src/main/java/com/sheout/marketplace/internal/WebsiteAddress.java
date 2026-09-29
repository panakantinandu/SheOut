package com.sheout.marketplace.internal;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * A seller's website, as a link customers can safely be sent to.
 * <p>
 * Only http and https: the link opens in a customer's browser from a
 * listing, so javascript:, data:, file: and the like are refused rather than
 * escaped. The host must look like a real domain (a dot, no spaces, no
 * credentials) - "mysite" or "localhost" is a typo or a trap, not a shop.
 * "www.example.com" is accepted and stored as https://www.example.com, the
 * way she would type it.
 */
final class WebsiteAddress {

    static final int MAX_LENGTH = 200;

    private WebsiteAddress() {
    }

    /** The cleaned address, or null when it is not a web address we will link to. */
    static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String value = typed.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH || value.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        if (!value.contains("://")) {
            value = "https://" + value;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null) {
            return null;
        }
        String ascii;
        try {
            ascii = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
        // A domain with a real top-level part: example.com, shop.co.in - not "mysite" or an IP.
        if (!ascii.matches("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) {
            return null;
        }
        return value.length() <= MAX_LENGTH ? value : null;
    }
}
