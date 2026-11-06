package com.capaciti.ops.user;

import com.capaciti.ops.security.AuthDtos.UserDto;
import com.capaciti.ops.security.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.capaciti.ops.security.AuthUser;
import com.capaciti.ops.common.Exceptions.ForbiddenException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * User management endpoints.
 * GET  /api/users/staff         – managers/admins: fetch assignable staff list.
 * POST /api/users               – ADMIN only: create a staff account (AGENT/MANAGER/ADMIN).
 * PATCH /api/users/{id}/deactivate – ADMIN only: soft-deactivate a user account.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Returns all AGENT and MANAGER accounts (used for ticket assignment UI). */
    @GetMapping("/staff")
    public List<UserDto> staff() {
        return users.findByRoleIn(List.of(Role.AGENT, Role.MANAGER))
                .stream().map(AuthService::toDto).toList();
    }

    /** Returns all users (ADMIN only – enforced by SecurityConfig). */
    @GetMapping
    public List<UserDto> all() {
        return users.findAll().stream().map(AuthService::toDto).toList();
    }

    /**
     * ADMIN creates a staff or admin account.
     * Self-registration via /api/auth/register always creates EMPLOYEE accounts.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest req,
                          @AuthenticationPrincipal AuthUser caller) {
        requireAdmin(caller);
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new com.capaciti.ops.common.Exceptions.BadRequestException("Email is already registered");
        }
        User u = new User();
        u.setEmail(req.email().trim().toLowerCase());
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        u.setDepartment(req.department());
        u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);
        return AuthService.toDto(u);
    }

    /** ADMIN soft-deactivates a user (sets active=false). */
    @PatchMapping("/{id}/deactivate")
    public UserDto deactivate(@PathVariable Long id, @AuthenticationPrincipal AuthUser caller) {
        requireAdmin(caller);
        User u = users.findById(id)
                .orElseThrow(() -> new com.capaciti.ops.common.Exceptions.NotFoundException("User not found"));
        u.setActive(false);
        users.save(u);
        return AuthService.toDto(u);
    }

    /** ADMIN reactivates a user. */
    @PatchMapping("/{id}/activate")
    public UserDto activate(@PathVariable Long id, @AuthenticationPrincipal AuthUser caller) {
        requireAdmin(caller);
        User u = users.findById(id)
                .orElseThrow(() -> new com.capaciti.ops.common.Exceptions.NotFoundException("User not found"));
        u.setActive(true);
        users.save(u);
        return AuthService.toDto(u);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void requireAdmin(AuthUser caller) {
        if (caller.role() != Role.ADMIN) {
            throw new ForbiddenException("Only ADMINs can manage user accounts");
        }
    }

    // ── inner DTOs ───────────────────────────────────────────────────────────

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            Role role,
            com.capaciti.ops.ticket.Category department) {}
}
