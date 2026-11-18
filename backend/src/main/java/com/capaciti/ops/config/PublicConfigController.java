package com.capaciti.ops.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, token-free settings the login screen needs before the user is authenticated.
 * Only add values here that are safe for anyone on the internet to read.
 */
@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    private final boolean demoMode;

    public PublicConfigController(@Value("${app.demo.show-accounts:false}") boolean demoMode) {
        this.demoMode = demoMode;
    }

    @GetMapping
    public Map<String, Boolean> config() {
        return Map.of("demoMode", demoMode);
    }
}
