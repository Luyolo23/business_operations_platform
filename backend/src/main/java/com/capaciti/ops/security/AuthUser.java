package com.capaciti.ops.security;

import com.capaciti.ops.user.Role;

/** The authenticated caller, available in controllers via @AuthenticationPrincipal. */
public record AuthUser(Long id, String email, Role role) {
    public boolean isStaff() { return role != Role.EMPLOYEE; }
    public boolean isManagerOrAdmin() { return role == Role.MANAGER || role == Role.ADMIN; }
}
