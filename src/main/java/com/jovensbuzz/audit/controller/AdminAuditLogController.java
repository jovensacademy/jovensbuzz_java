package com.jovensbuzz.audit.controller;

import com.jovensbuzz.audit.dto.AuditLogDto;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin Audit Logs", description = "Query admin action audit history")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @Operation(summary = "Get audit logs", description = "Paginated list of system and administrative audit logs")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        PagedResponse<AuditLogDto> logs = auditLogService.getAuditLogs(pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
