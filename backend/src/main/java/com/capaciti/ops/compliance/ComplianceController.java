package com.capaciti.ops.compliance;

import com.capaciti.ops.common.PageResponse;
import com.capaciti.ops.compliance.ComplianceDtos.AuditLogDto;
import com.capaciti.ops.compliance.ComplianceDtos.Overview;
import com.capaciti.ops.compliance.ComplianceDtos.RiskReport;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {

    private final ComplianceService service;

    public ComplianceController(ComplianceService service) { this.service = service; }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(defaultValue = "30") int days) { return service.overview(days); }

    @GetMapping("/report")
    public RiskReport report(@RequestParam(defaultValue = "30") int days) { return service.report(days); }

    @GetMapping("/audit")
    public PageResponse<AuditLogDto> audit(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return service.audit(page, size);
    }
}
