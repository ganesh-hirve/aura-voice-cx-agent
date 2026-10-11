package com.ganesh.aura.normalization;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes only explicit three-digit Aura order identifiers. */
public final class OrderIdNormalizer {

    private static final Pattern SUPPORTED_FORM = Pattern.compile(
            "(?:ORD-|ORDER\\s+)?([0-9]{3})", Pattern.CASE_INSENSITIVE);

    private OrderIdNormalizer() {
    }

    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        Matcher matcher = SUPPORTED_FORM.matcher(input.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of("ORD-" + matcher.group(1));
    }

    /** Returns a token-boundary pattern for the supported spellings of a canonical ID. */
    public static Pattern mentionPattern(String canonicalOrderId) {
        String suffix = canonicalOrderId.toUpperCase(Locale.ROOT).substring("ORD-".length());
        return Pattern.compile("(?<![A-Za-z0-9])(?:ORD-" + suffix
                + "|ORDER\\s+" + suffix + "|" + suffix + ")(?![A-Za-z0-9])",
                Pattern.CASE_INSENSITIVE);
    }
}
