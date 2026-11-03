package com.jovensbuzz.dashboard.controller;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.dashboard.dto.DashboardSummaryDto;
import com.jovensbuzz.dashboard.dto.EnquiryTrendDto;
import com.jovensbuzz.dashboard.dto.ServiceDistributionDto;
import com.jovensbuzz.dashboard.dto.StatusDistributionDto;
import com.jovensbuzz.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Admin Dashboard", description = "Live metrics, trends, and distributions computed from stored enquiries")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final AdminUserRepository adminUserRepository;

    public AdminDashboardController(DashboardService dashboardService, AdminUserRepository adminUserRepository) {
        this.dashboardService = dashboardService;
        this.adminUserRepository = adminUserRepository;
    }

    /**
     * The requested zone, or the caller's own display zone when tz is omitted, so the dashboard can be
     * requested without first fetching the profile (one round trip less per page).
     */
    private ZoneId zone(String tz, UserDetails user) {
        if (tz != null && !tz.isBlank()) return DashboardService.parseZone(tz);
        String accountZone = user == null ? null
                : adminUserRepository.findByUsername(user.getUsername()).map(AdminUser::getTimezone).orElse(null);
        return DashboardService.parseZone(null, accountZone);
    }

    @GetMapping("/summary")
    @Operation(summary = "Get dashboard metrics summary", description = "Totals, pipeline stage counts, today/month volume and sync health")
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> getSummary(@RequestParam(required = false) String tz,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getSummary(zone(tz, user))));
    }

    @GetMapping("/enquiry-trends")
    @Operation(summary = "Get enquiry volume trends",
            description = "Daily enquiry counts between 'from' and 'to' (ISO dates, inclusive). Defaults to the last 'days' days.")
    public ResponseEntity<ApiResponse<List<EnquiryTrendDto>>> getEnquiryTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(required = false) String tz,
            @AuthenticationPrincipal UserDetails user) {
        ZoneId zone = zone(tz, user);
        if (from == null) {
            LocalDate end = to != null ? to : LocalDate.now(zone);
            from = end.minusDays(Math.min(Math.max(days, 1), 366) - 1L);
        }
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getEnquiryTrends(from, to, zone)));
    }

    @GetMapping("/service-distribution")
    @Operation(summary = "Get service interest distribution", description = "Enquiries grouped by requested service, optionally within a date range")
    public ResponseEntity<ApiResponse<List<ServiceDistributionDto>>> getServiceDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String tz,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getServiceDistribution(from, to, zone(tz, user))));
    }

    @GetMapping("/status-distribution")
    @Operation(summary = "Get enquiry status distribution", description = "Enquiries grouped by pipeline status, optionally within a date range")
    public ResponseEntity<ApiResponse<List<StatusDistributionDto>>> getStatusDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String tz,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getStatusDistribution(from, to, zone(tz, user))));
    }
}
