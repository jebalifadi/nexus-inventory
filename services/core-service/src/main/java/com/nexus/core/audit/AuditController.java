package com.nexus.core.audit;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The audit log, newest first. Only the owner can read it. */
@RestController
@RequestMapping("/api/v1/audit")
class AuditController {

    private final AuditRepository audit;

    AuditController(AuditRepository audit) {
        this.audit = audit;
    }

    @GetMapping
    List<AuditEntry> latest(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return audit.latest(Math.max(1, Math.min(limit, 1000)));
    }
}
