package com.capaciti.ops.security;

import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(@NotBlank @Size(max = 120) String fullName,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 100) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserDto(Long id, String email, String fullName, Role role, Category department) {}

    public record AuthResponse(String token, long expiresInSeconds, UserDto user) {}
}
