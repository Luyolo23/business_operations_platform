package com.capaciti.ops.ticket;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    List<Ticket> findByCreatedAtAfter(Instant since);
    List<Ticket> findByCreatedAtAfterAndCategory(Instant since, Category category);
    long countByAssigneeIdAndStatusIn(Long assigneeId, Collection<TicketStatus> statuses);
    List<Ticket> findByStatusInAndEscalatedFalse(Collection<TicketStatus> statuses);
    long countByRequester_Id(Long requesterId);
    long countByAssignee_Id(Long assigneeId);
}
