package com.capaciti.ops.security;

import com.capaciti.ops.common.Exceptions.BadRequestException;
import com.capaciti.ops.common.Exceptions.NotFoundException;
import com.capaciti.ops.compliance.AuditService;
import com.capaciti.ops.security.AuthDtos.*;
import com.capaciti.ops.user.Role;
import com.capaciti.ops.user.User;
import com.capaciti.ops.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }

    /** Self-registration always creates an EMPLOYEE. Staff accounts are created by an ADMIN (or the seeder). */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new BadRequestException("Email is already registered");
        }
        User u = new User();
        u.setEmail(req.email().trim().toLowerCase());
        u.setFullName(req.fullName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.EMPLOYEE);
        users.save(u);
        audit.log("USER_REGISTERED", "User", u.getId(), u.getEmail(), null);
        return toResponse(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmailIgnoreCase(req.email())
                .filter(found -> encoder.matches(req.password(), found.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return toResponse(u);
    }

    @Transactional(readOnly = true)
    public UserDto me(AuthUser caller) {
        User u = users.findById(caller.id()).orElseThrow(() -> new NotFoundException("User not found"));
        return toDto(u);
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwt.generate(u), jwt.expiresInSeconds(), toDto(u));
    }

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getDepartment());
    }
}
