package com.sheout.auth.internal;

/**
 * Turns a user-agent string into something a person recognises in a list of
 * her own devices.
 * <p>
 * Deliberately coarse. "Android phone" is what she can match against the
 * phone in her hand; a version-by-version reading of the user agent tells her
 * nothing more and would be wrong more often. Anything unrecognised is
 * "Unknown device", which is honest and still lets her sign it out.
 */
final class DeviceLabel {

    private DeviceLabel() {
    }

    static String from(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("ipad")) return "iPad";
        if (ua.contains("iphone")) return "iPhone";
        if (ua.contains("android")) return ua.contains("mobile") ? "Android phone" : "Android tablet";
        if (ua.contains("windows")) return "Windows computer";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "Mac";
        if (ua.contains("cros")) return "Chromebook";
        if (ua.contains("linux")) return "Linux computer";
        return "Unknown device";
    }
}
