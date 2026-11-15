package com.capaciti.ops.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicConfigControllerTest {

    @Test
    void reportsDemoModeWhenTheFlagIsEnabled() {
        assertEquals(Map.of("demoMode", true), new PublicConfigController(true).config());
    }

    @Test
    void reportsDemoModeOffWhenTheFlagIsDisabled() {
        assertEquals(Map.of("demoMode", false), new PublicConfigController(false).config());
    }
}
