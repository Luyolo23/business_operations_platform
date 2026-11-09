package com.capaciti.ops.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TicketDtos {
    private TicketDtos() {}

    public record CreateTicketRequest(@NotBlank @Size(max = 200) String title,
                                      @NotBlank @Size(max = 5000) String description) {}

    public record UpdateStatusRequest(@NotNull TicketStatus status) {}

    public record UpdateClassificationRequest(@NotNull Category category, @NotNull Priority priority) {}

    public record AssignRequest(@NotNull Long userId) {}

    /** The requester confirms the fix worked, or says the problem is still there. */
    public record ResolutionConfirmationRequest(@NotNull Boolean confirmed,
                                                @Size(max = 2000) String comment) {}

    public record TicketDto(Long id, String title, String description, Category category, Priority priority,
                            TicketStatus status, Long requesterId, String requesterName, String requesterEmail,
                            Long assigneeId, String assigneeName, Double classificationConfidence,
                            String classificationSource, boolean escalated, Instant createdAt, Instant updatedAt,
                            Instant firstResponseAt, Instant resolvedAt, Instant requesterConfirmedAt,
                            String requesterFeedback) {
        public static TicketDto from(Ticket t) {
            return new TicketDto(t.getId(), t.getTitle(), t.getDescription(), t.getCategory(), t.getPriority(),
                    t.getStatus(), t.getRequester().getId(), t.getRequester().getFullName(),
                    t.getRequester().getEmail(),
                    t.getAssignee() == null ? null : t.getAssignee().getId(),
                    t.getAssignee() == null ? null : t.getAssignee().getFullName(),
                    t.getClassificationConfidence(), t.getClassificationSource(), t.isEscalated(),
                    t.getCreatedAt(), t.getUpdatedAt(), t.getFirstResponseAt(), t.getResolvedAt(),
                    t.getRequesterConfirmedAt(), t.getRequesterFeedback());
        }
    }
}
