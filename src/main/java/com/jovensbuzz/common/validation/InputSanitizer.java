package com.jovensbuzz.common.validation;

import java.util.regex.Pattern;

/**
 * Normalises free-text input before it is stored. All content is rendered as text by the
 * frontend (React escapes it), so this is defence in depth: it removes markup, script-like
 * fragments and control characters rather than trying to allow "safe" HTML.
 */
public final class InputSanitizer {

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ANGLE_BRACKETS = Pattern.compile("[<>]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern INLINE_WHITESPACE = Pattern.compile("[ \\t]+");
    private static final Pattern EXCESS_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern SCRIPT_SCHEMES = Pattern.compile("(?i)(javascript|vbscript|data)\\s*:");
    // Leading characters that spreadsheet apps interpret as formulas
    private static final Pattern FORMULA_PREFIX = Pattern.compile("^[=+\\-@\\t\\r]");

    private InputSanitizer() {}

    /**
     * Single-line value: tags and control characters removed, whitespace collapsed.
     */
    public static String plainText(String input) {
        if (input == null) return null;
        String value = multiLineText(input);
        return value == null ? null : value.replace('\n', ' ').replaceAll(" {2,}", " ").trim();
    }

    /**
     * Multi-line value (messages, descriptions): keeps line breaks, strips markup.
     */
    public static String multiLineText(String input) {
        if (input == null) return null;
        String value = input.replace("\r\n", "\n").replace('\r', '\n');
        value = TAGS.matcher(value).replaceAll("");
        value = ANGLE_BRACKETS.matcher(value).replaceAll("");
        value = SCRIPT_SCHEMES.matcher(value).replaceAll("");
        value = CONTROL_CHARS.matcher(value).replaceAll("");
        value = INLINE_WHITESPACE.matcher(value).replaceAll(" ");
        value = EXCESS_NEWLINES.matcher(value).replaceAll("\n\n");
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Makes a value safe to place in a spreadsheet cell or CSV (prevents formula injection).
     */
    public static String spreadsheetSafe(String input) {
        if (input == null) return "";
        return FORMULA_PREFIX.matcher(input).find() ? "'" + input : input;
    }

    /**
     * Accepts only absolute http(s) URLs; returns null otherwise.
     */
    public static String safeUrl(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.isEmpty()) return "";
        if (!value.matches("(?i)^https?://[^\\s<>\"']+$")) return null;
        return value;
    }
}
