package com.capaciti.ops.compliance;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    List<RiskAssessment> findTop20ByLevelInOrderByCreatedAtDesc(List<RiskLevel> levels);
    List<RiskAssessment> findByResponseIdIn(Collection<Long> responseIds);
    long countByLevel(RiskLevel level);
}
