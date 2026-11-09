package com.capaciti.ops.response;

import com.capaciti.ops.compliance.RiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ResponseDtos {
    private ResponseDtos() {}

    /**
     * Either generate an AI/template reply (leave {@code body} null) or send a manually
     * written reply ({@code body} non-blank). Manual replies still go through the same
     * risk screening and approval pipeline.
     */
    public record GenerateResponseRequest(@NotNull Tone tone, @Size(max = 10000) String body) {}

    public record ResponseDto(Long id, Long ticketId, Tone tone, String body, String generatedBy,
                              String transparencyNote, ResponseStatus status, RiskLevel riskLevel,
                              List<String> riskFlags, String riskNotes, Long approvalId, Instant createdAt) {}

    public record ApprovalDto(Long id, Long responseId, Long ticketId, String ticketTitle, String responseBody,
                              RiskLevel riskLevel, List<String> riskFlags, ApprovalStatus status,
                              String decidedBy, String decisionComment, Instant createdAt, Instant decidedAt) {}

    public record DecisionRequest(boolean approve, String comment) {}
}
