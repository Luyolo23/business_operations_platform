package com.capaciti.ops.ai;

import com.capaciti.ops.common.TextSafety;
import java.util.regex.Pattern;

/**
 * Wraps untrusted ticket content so the model can tell data from instructions. The markers
 * are stripped from the input first, otherwise a requester could close the block early and
 * inject instructions of their own.
 */
public final class PromptSafety {

    public static final String START = "[[[UNTRUSTED_DATA_START]]]";
    public static final String END = "[[[UNTRUSTED_DATA_END]]]";

    private static final Pattern MARKERS = Pattern.compile(
            Pattern.quote(START) + "|" + Pattern.quote(END), Pattern.CASE_INSENSITIVE);

    private PromptSafety() {
    }

    public static String clean(String text) {
        return MARKERS.matcher(TextSafety.cleanReply(text)).replaceAll("").strip();
    }

    public static String wrap(String text) {
        return START + "\n" + clean(text) + "\n" + END;
    }
}
