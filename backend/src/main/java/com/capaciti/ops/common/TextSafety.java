package com.capaciti.ops.common;

import java.text.Normalizer;

/**
 * Normalises untrusted text before it is stored, screened or written to the audit trail.
 * Unusual Unicode (full-width letters, zero-width spaces, NUL bytes) must not be able to
 * hide risky wording from the rule-based risk checker.
 */
public final class TextSafety {

    private TextSafety() {
    }

    /** NFKC-normalise and remove control/format characters, keeping normal line breaks and tabs. */
    public static String cleanReply(String text) {
        if (text == null) return "";
        String normalised = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(normalised.length());
        normalised.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t') {
                out.appendCodePoint(cp);
            } else if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) {
                // drop invisible/control characters such as NUL, zero-width spaces and bidi overrides
            } else {
                out.appendCodePoint(cp);
            }
        });
        return out.toString().strip();
    }

    /** Collapse all whitespace to single spaces and cap the value, for one-line fields. */
    public static String oneLine(String text, int maxLength) {
        String cleaned = cleanReply(text).replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ").strip();
        if (maxLength > 0 && cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength).strip();
        }
        return cleaned;
    }

    /** Audit details are displayed as one row and must never contain embedded newlines. */
    public static String auditDetail(String text) {
        return oneLine(text, 4000);
    }
}
