package xyz.e3ndr.tor_clearnet;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Rewriter {
    private static final String PART_SUBDOMAIN = "([A-Za-z0-9\\-]+\\.)*";
    private static final String PART_ONION = "([a-z2-7]{16}|[a-z2-7]{56})\\.onion";

    private static final Pattern HTTPS_PATTERN = Pattern.compile("https:\\/\\/" + PART_SUBDOMAIN + PART_ONION);
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(PART_SUBDOMAIN + PART_ONION);

    public static String rewriteCsp(String header) {
        List<String> newDirectives = new LinkedList<>();

        for (String directive : header.split(";")) {
            List<String> newParts = new LinkedList<>();

            for (String part : directive.split(" ")) {
                if (!part.contains(".onion")) {
                    newParts.add(part);
                    continue;
                }

                if (part.startsWith("wss://")) {
                    part = part.substring("wss://".length());
                } else if (part.startsWith("ws://")) {
                    part = part.substring("ws://".length());
                } else if (part.startsWith("https://")) {
                    part = part.substring("https://".length());
                } else if (part.startsWith("http://")) {
                    part = part.substring("http://".length());
                }

                part = String.format("https-%s %s", part, part);

                newParts.add(part);
            }

            newDirectives.add(String.join(" ", newParts));
        }

        return String.join(" ", newDirectives);
    }

    public static String rewrite(String input) {
        if (TorClearnet.SERVICE_SUPPORTS_HTTPS) {
            input = regexRewrite(HTTPS_PATTERN, input, (match) -> "https://https-" + match.substring("https://".length()));
        } else {
            input = regexRewrite(HTTPS_PATTERN, input, (match) -> "http://https-" + match.substring("https://".length()));
        }

        if (TorClearnet.SERVICE_DOMAIN != null) { // We're NOT in DNS-only mode.
            input = regexRewrite(DOMAIN_PATTERN, input, (match) -> match.substring(0, match.length() - "onion".length()) + TorClearnet.SERVICE_DOMAIN);
        }

        return input;
    }

    private static String regexRewrite(Pattern pattern, String input, Function<String, String> transformer) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder result = new StringBuilder(input);

        int offset = 0;
        while (matcher.find()) {
            String match = matcher.group();
            String modified = transformer.apply(match);

            result.replace(
                matcher.start() + offset,
                matcher.end() + offset,
                modified
            );

            offset += modified.length() - match.length();
        }

        return result.toString();
    }

}
