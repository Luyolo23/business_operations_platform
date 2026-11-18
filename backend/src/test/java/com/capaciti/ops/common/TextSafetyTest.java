package com.capaciti.ops.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TextSafetyTest {

    @Test
    void normalisesFullWidthAndRemovesFormatCharacters() {
        assertEquals("We guarantee this", TextSafety.cleanReply("We \uFF47\uFF55\uFF41\uFF52\uFF41\uFF4E\uFF54\uFF45\uFF45\u200B this"));
    }

    @Test
    void keepsNormalLineBreaksButRemovesControlCharacters() {
        assertEquals("one\ntwo\tthree", TextSafety.cleanReply("one\ntwo\u0000\tthree"));
    }

    @Test
    void oneLineCollapsesWhitespaceAndCapsLength() {
        String result = TextSafety.oneLine("a\nb\t\tc   d", 5);
        assertEquals("a b c", result);
        assertFalse(result.contains("\n"));
    }

    @Test
    void nullAndBlankInputsAreSafe() {
        assertEquals("", TextSafety.cleanReply(null));
        assertEquals("", TextSafety.oneLine(null, 10));
        assertEquals("", TextSafety.auditDetail("   "));
    }
}
