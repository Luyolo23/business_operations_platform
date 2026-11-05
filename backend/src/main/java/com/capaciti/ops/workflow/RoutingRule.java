package com.capaciti.ops.workflow;

import com.capaciti.ops.ticket.Category;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "routing_rules")
public class RoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, unique = true)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_department", nullable = false)
    private Category targetDepartment;

    @Column(name = "sla_hours", nullable = false)
    private int slaHours;

    @Column(name = "active", nullable = false)
    private boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Category getTargetDepartment() { return targetDepartment; }
    public void setTargetDepartment(Category targetDepartment) { this.targetDepartment = targetDepartment; }

    public int getSlaHours() { return slaHours; }
    public void setSlaHours(int slaHours) { this.slaHours = slaHours; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

}
