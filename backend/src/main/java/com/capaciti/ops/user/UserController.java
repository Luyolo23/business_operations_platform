package com.capaciti.ops.user;

import com.capaciti.ops.common.Exceptions.BadRequestException;
import com.capaciti.ops.common.Exceptions.ForbiddenException;
import com.capaciti.ops.common.Exceptions.NotFoundException;
import com.capaciti.ops.compliance.AuditService;
import com.capaciti.ops.security.AuthDtos.UserDto;
import com.capaciti.ops.security.AuthService;
import com.capaciti.ops.security.AuthUser;
import com.capaciti.ops.ticket.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * User management endpoints.
 * GET  /api/users/staff              – managers/admins: assignable staff list.
 * GET  /api/users                    – ADMIN only: every account, including deactivated ones.
 * POST /api/users                    – ADMIN only: create an EMPLOYEE/AGENT/MANAGER/ADMIN account.
 * PATCH /api/users/{id}/deactivate   – ADMIN only: soft-deactivate an account.
 * PATCH /api/users/{id}/activate     – ADMIN only: reactivate an account.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserController(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** Returns active AGENT and MANAGER accounts (used for ticket assignment). */
    @GetMapping("/staff")
    public List<UserDto> staff() {
        return users.findByRoleInAndActiveTrue(List.of(Role.AGENT, Role.MANAGER))
                .stream().map(AuthService::toDto).toList();
    }

    /** Returns all users, active or not (ADMIN only – enforced by SecurityConfig). */
    @GetMapping
    public List<UserDto> all() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(AuthService::toDto).toList();
    }

    /** ADMIN creates an account. Self-registration via /api/auth/register remains EMPLOYEE-only. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserDto create(@Valid @RequestBody CreateUserRequest req,
                          @AuthenticationPrincipal AuthUser caller) {
        requireAdmin(caller);
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Email is already registered");
        }
        if (req.role() == Role.AGENT && req.department() == null) {
            throw new BadRequestException("Agents need a department");
        }
        if (req.role() != Role.AGENT && req.department() != null) {
            throw new BadRequestException("Only agents belong to a department");
        }

        User u = new User();
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        u.setDepartment(req.department());
        u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);
        audit.log("USER_CREATED", "User", u.getId(), caller.email(),
                "role=" + u.getRole() + " department=" + u.getDepartment());
        return AuthService.toDto(u);
    }

    /** ADMIN soft-deactivates a user (sets active=false). */
    @PatchMapping("/{id}/deactivate")
    @Transactional
    public UserDto deactivate(@PathVariable Long id, @AuthenticationPrincipal AuthUser caller) {
        requireAdmin(caller);
        User u = find(id);
        if (u.getId().equals(caller.id())) {
            throw new BadRequestException("You cannot deactivate your own account");
        }
        if (u.getRole() == Role.ADMIN && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new BadRequestException("Cannot deactivate the last active admin");
        }
        if (u.isActive()) {
            u.setActive(false);
            users.save(u);
            audit.log("USER_DEACTIVATED", "User", u.getId(), caller.email(), "email=" + u.getEmail());
        }
        return AuthService.toDto(u);
    }

    /** ADMIN reactivates a user. */
    @PatchMapping("/{id}/activate")
    @Transactional
    public UserDto activate(@PathVariable Long id, @AuthenticationPrincipal AuthUser caller) {
        requireAdmin(caller);
        User u = find(id);
        if (!u.isActive()) {
            u.setActive(true);
            users.save(u);
            audit.log("USER_ACTIVATED", "User", u.getId(), caller.email(), "email=" + u.getEmail());
        }
        return AuthService.toDto(u);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

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
            @NotNull Role role,
            Category department) {}
}
