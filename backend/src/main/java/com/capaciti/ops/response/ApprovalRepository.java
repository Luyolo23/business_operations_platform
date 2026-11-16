package com.capaciti.ops.response;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);
    List<Approval> findByResponseIdIn(Collection<Long> responseIds);
    boolean existsByStatusAndResponseTicketId(ApprovalStatus status, Long ticketId);
    long countByStatus(ApprovalStatus status);
    long countByStatusAndDecidedAtAfter(ApprovalStatus status, Instant since);
    long countByDecidedBy_Id(Long userId);
}
