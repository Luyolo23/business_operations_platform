package com.capaciti.ops.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {

    @Test
    void normalisesAndTrimsConfigurationValues() {
        var p = new AiProperties(" LLM ", " http://localhost:1234 ", " key ", " model ", 20);
        assertEquals("llm", p.provider());
        assertEquals("http://localhost:1234", p.baseUrl());
        assertEquals("key", p.apiKey());
        assertEquals("model", p.model());
    }

    @Test
    void rejectsAnUnknownProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiProperties("openai", "https://example.com", "key", "model", 20));
    }

    @Test
    void rejectsATimeoutOutsideTheSafeRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiProperties("mock", "https://example.com", "", "model", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AiProperties("mock", "https://example.com", "", "model", 121));
    }

    @Test
    void rejectsAMalformedBaseUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiProperties("llm", "not a url", "key", "model", 20));
        assertThrows(IllegalArgumentException.class,
                () -> new AiProperties("llm", "ftp://example.com", "key", "model", 20));
    }

    @Test
    void rejectsABlankModel() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiProperties("llm", "https://example.com", "key", "  ", 20));
    }

    @Test
    void rejectsControlCharactersInTheApiKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiProperties("llm", "https://example.com", "key\nleak", "model", 20));
    }
}
