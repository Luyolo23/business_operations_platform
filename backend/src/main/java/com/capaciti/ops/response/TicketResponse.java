package com.capaciti.ops.response;

import com.capaciti.ops.compliance.RiskLevel;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ticket_responses")
public class TicketResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "tone", nullable = false)
    private Tone tone;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "generated_by", nullable = false)
    private String generatedBy;

    @Column(name = "transparency_note", columnDefinition = "text")
    private String transparencyNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ResponseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }

    public Tone getTone() { return tone; }
    public void setTone(Tone tone) { this.tone = tone; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }

    public String getTransparencyNote() { return transparencyNote; }
    public void setTransparencyNote(String transparencyNote) { this.transparencyNote = transparencyNote; }

    public ResponseStatus getStatus() { return status; }
    public void setStatus(ResponseStatus status) { this.status = status; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
