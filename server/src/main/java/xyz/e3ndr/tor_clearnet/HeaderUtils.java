package xyz.e3ndr.tor_clearnet;

import co.casterlabs.rhs.protocol.HeaderValue;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class HeaderUtils {

    public static String getOnionAddress(String host) {
        if (host.endsWith(".onion")) {
            // We're being used as a DNS-based proxy. Yay!
            return host;
        }

        for (String domain : TorClearnet.DOMAINS) {
            if (host.endsWith(domain)) {
                return host.substring(0, host.length() - domain.length());
            }
        }

        return null; // Unrecognized clearnet domain name
    }

    public static boolean isTextType(String mime) {
        if (mime == null) return false;

        mime = mime.toLowerCase();

        if (mime.startsWith("text/")) return true;
        if (mime.contains("+text")) return true;

        if (mime.contains("json")) return true;
        if (mime.contains("xml")) return true;
        if (mime.contains("csv")) return true;
        if (mime.contains("javascript")) return true;
        if (mime.contains("application/wasm")) return true;

        return false;
    }

    private static boolean h_eq(HttpSession session, String header, String value) {
        return session.headers().getSingleOrDefault(header, HeaderValue.EMPTY).raw().equalsIgnoreCase(value);
    }

    public static boolean isSecure(HttpSession session) {
        return h_eq(session, "X-Forwarded-Proto", "https") ||
            h_eq(session, "X-Url-Scheme", "https") ||
            h_eq(session, "Front-End-Https", "on") ||
            h_eq(session, "X-Forwarded-Ssl", "on");
    }

}
