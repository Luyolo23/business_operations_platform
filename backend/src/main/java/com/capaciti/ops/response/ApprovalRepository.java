package com.capaciti.ops.response;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);
    List<Approval> findByResponseIdIn(Collection<Long> responseIds);
    long countByStatus(ApprovalStatus status);
}
