package com.capaciti.ops.response;

import com.capaciti.ops.response.ResponseDtos.ApprovalDto;
import com.capaciti.ops.response.ResponseDtos.DecisionRequest;
import com.capaciti.ops.security.AuthUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) { this.service = service; }

    @GetMapping
    public List<ApprovalDto> list(@RequestParam(required = false) ApprovalStatus status) {
        return service.list(status);
    }

    @PostMapping("/{id}/decision")
    public ApprovalDto decide(@PathVariable Long id, @RequestBody DecisionRequest req,
                              @AuthenticationPrincipal AuthUser caller) {
        return service.decide(id, req.approve(), req.comment(), caller);
    }
}
