package com.capaciti.ops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capaciti.ops.user.Role;
import com.capaciti.ops.user.User;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final long EXPIRATION_MINUTES = 60;

    @Test
    void rejectsABlankSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtService("", EXPIRATION_MINUTES));
        assertTrue(ex.getMessage().toLowerCase().contains("jwt_secret"),
                "the error should name the missing setting, was: " + ex.getMessage());
    }

    @Test
    void rejectsASecretShorterThan32Bytes() {
        String tooShort = "0123456789012345678901234567890"; // 31 ASCII bytes
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtService(tooShort, EXPIRATION_MINUTES));
        assertTrue(ex.getMessage().contains("32"),
                "the error should explain the 32-byte minimum, was: " + ex.getMessage());
    }

    @Test
    void acceptsASecretOfExactly32BytesAndRoundTripsAToken() {
        String secret = "01234567890123456789012345678901"; // 32 ASCII bytes
        JwtService jwt = new JwtService(secret, EXPIRATION_MINUTES);

        User user = new User();
        user.setId(42L);
        user.setEmail("agent@ops.local");
        user.setRole(Role.AGENT);

        String token = jwt.generate(user);

        AuthUser parsed = jwt.parse(token).orElseThrow();
        assertEquals(42L, parsed.id());
        assertEquals("agent@ops.local", parsed.email());
        assertEquals(Role.AGENT, parsed.role());
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        JwtService issuer = new JwtService("01234567890123456789012345678901", EXPIRATION_MINUTES);
        JwtService verifier = new JwtService("abcdefghijklmnopqrstuvwxyz012345", EXPIRATION_MINUTES);

        User user = new User();
        user.setId(1L);
        user.setEmail("someone@ops.local");
        user.setRole(Role.EMPLOYEE);

        assertTrue(verifier.parse(issuer.generate(user)).isEmpty());
    }
}
