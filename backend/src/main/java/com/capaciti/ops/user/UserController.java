package com.capaciti.ops.user;

import com.capaciti.ops.security.AuthDtos.UserDto;
import com.capaciti.ops.security.AuthService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Managers/admins need the staff list to (re)assign tickets. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) { this.users = users; }

    @GetMapping("/staff")
    public List<UserDto> staff() {
        return users.findByRoleIn(List.of(Role.AGENT, Role.MANAGER)).stream().map(AuthService::toDto).toList();
    }
}
