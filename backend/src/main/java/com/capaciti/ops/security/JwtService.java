package com.capaciti.ops.security;

import com.capaciti.ops.user.Role;
import com.capaciti.ops.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Fail at startup instead of at the first login attempt. A short/blank secret would
            // otherwise surface as an opaque jjwt WeakKeyException or, worse, sign weak tokens.
            throw new IllegalStateException(
                    "JWT_SECRET (app.jwt.secret) must be at least 32 bytes for HS256; got "
                            + keyBytes.length + ". Set the JWT_SECRET environment variable.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMinutes = expirationMinutes;
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

            // A valid signature only proves the token came from us. The claims must also be
            // present and usable; otherwise callers would hit a NullPointerException later.
            Object uidClaim = c.get("uid");
            if (!(uidClaim instanceof Number uid)) return Optional.empty();

            String roleClaim = c.get("role", String.class);
            if (roleClaim == null) return Optional.empty();
            Role role;
            try {
                role = Role.valueOf(roleClaim);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }

            String email = c.getSubject();
            if (email == null || email.isBlank()) return Optional.empty();

            return Optional.of(new AuthUser(uid.longValue(), email, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long expiresInSeconds() { return expirationMinutes * 60; }
}
