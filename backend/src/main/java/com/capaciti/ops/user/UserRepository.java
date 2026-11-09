package com.capaciti.ops.user;

import com.capaciti.ops.ticket.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleAndDepartment(Role role, Category department);
    List<User> findByRoleIn(List<Role> roles);
    List<User> findByRoleInAndActiveTrue(List<Role> roles);
    long countByRoleAndActiveTrue(Role role);
}
