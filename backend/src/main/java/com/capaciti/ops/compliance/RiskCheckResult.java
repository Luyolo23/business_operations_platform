package com.capaciti.ops.compliance;

import java.util.List;

public record RiskCheckResult(RiskLevel level, List<String> flags, String notes) {
}
