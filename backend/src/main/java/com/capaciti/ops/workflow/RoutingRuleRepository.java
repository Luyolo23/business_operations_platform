package com.capaciti.ops.workflow;

import com.capaciti.ops.ticket.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {
    Optional<RoutingRule> findByCategoryAndActiveTrue(Category category);
}
