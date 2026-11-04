package com.capaciti.ops.compliance;

import com.capaciti.ops.response.TicketResponse;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id")
    private TicketResponse response;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private RiskLevel level;

    @Column(name = "flags", columnDefinition = "text")
    private String flags;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TicketResponse getResponse() { return response; }
    public void setResponse(TicketResponse response) { this.response = response; }

    public RiskLevel getLevel() { return level; }
    public void setLevel(RiskLevel level) { this.level = level; }

    public String getFlags() { return flags; }
    public void setFlags(String flags) { this.flags = flags; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
