package com.jovensbuzz.admin.controller;

import com.jovensbuzz.admin.dto.AdminSummaryDto;
import com.jovensbuzz.admin.dto.IntegrationStatusDto;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.admin.service.IntegrationSettingsService;
import com.jovensbuzz.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Settings", description = "Administrators, integration health and safe configuration")
@SecurityRequirement(name = "bearerAuth")
public class AdminSettingsController {

    private final AdminUserRepository adminUserRepository;
    private final IntegrationSettingsService integrationSettingsService;

    public AdminSettingsController(AdminUserRepository adminUserRepository,
                                   IntegrationSettingsService integrationSettingsService) {
        this.adminUserRepository = adminUserRepository;
        this.integrationSettingsService = integrationSettingsService;
    }

    @GetMapping("/users")
    @Operation(summary = "List administrators", description = "Enabled administrators available for lead assignment")
    public ResponseEntity<ApiResponse<List<AdminSummaryDto>>> listAdmins() {
        List<AdminSummaryDto> admins = adminUserRepository.findAll().stream()
                .filter(admin -> admin.isActive())
                .map(AdminSummaryDto::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(admins));
    }

    @GetMapping("/settings/integrations/google-sheets")
    @Operation(summary = "Google Sheets integration status", description = "Configuration health and sync counters; never returns secrets")
    public ResponseEntity<ApiResponse<IntegrationStatusDto>> googleSheetsStatus() {
        return ResponseEntity.ok(ApiResponse.success(integrationSettingsService.getGoogleSheetsStatus()));
    }

    @GetMapping("/settings/integrations/email")
    @Operation(summary = "Email (SMTP) integration status", description = "Whether SMTP is configured, sender identity and delivery counters; never returns credentials")
    public ResponseEntity<ApiResponse<Map<String, Object>>> emailStatus() {
        return ResponseEntity.ok(ApiResponse.success(integrationSettingsService.getEmailStatus()));
    }

    @PostMapping("/settings/integrations/google-sheets/retry-failed")
    @Operation(summary = "Retry failed syncs", description = "Re-queues every FAILED Google Sheets job; the background worker processes them")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> retryFailed(@AuthenticationPrincipal UserDetails userDetails) {
        int queued = integrationSettingsService.requeueFailed(userDetails != null ? userDetails.getUsername() : null);
        return ResponseEntity.ok(ApiResponse.success(queued + " job(s) re-queued", Map.of("queued", queued)));
    }
}
