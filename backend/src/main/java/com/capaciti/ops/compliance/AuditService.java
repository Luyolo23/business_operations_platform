package com.capaciti.ops.compliance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Append-only audit trail. Every important state change in the platform calls this. */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String action, String entityType, Long entityId, String actorEmail, String details) {
        AuditLog a = new AuditLog();
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setActorEmail(actorEmail);
        a.setDetails(details);
        repo.save(a);
    }
}
