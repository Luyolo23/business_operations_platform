package com.capaciti.ops.response;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketResponseRepository extends JpaRepository<TicketResponse, Long> {
    List<TicketResponse> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
