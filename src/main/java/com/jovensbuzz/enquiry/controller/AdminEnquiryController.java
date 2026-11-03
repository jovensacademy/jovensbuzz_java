package com.jovensbuzz.enquiry.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.common.response.PagedResponse;
import com.jovensbuzz.enquiry.dto.*;
import com.jovensbuzz.enquiry.service.EnquiryService;
import com.jovensbuzz.integration.email.dto.EmailNotificationOverviewDto;
import com.jovensbuzz.integration.email.service.EmailNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/enquiries")
@Tag(name = "Admin Enquiries", description = "Admin enquiry management, status updates, notes, assignment, sync and CSV export")
@SecurityRequirement(name = "bearerAuth")
public class AdminEnquiryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final EnquiryService enquiryService;
    private final EmailNotificationService emailNotificationService;

    public AdminEnquiryController(EnquiryService enquiryService, EmailNotificationService emailNotificationService) {
        this.enquiryService = enquiryService;
        this.emailNotificationService = emailNotificationService;
    }

    @GetMapping
    @Operation(summary = "List enquiries", description = "Filtered, sorted and paginated list of enquiries")
    public ResponseEntity<ApiResponse<PagedResponse<EnquiryResponseDto>>> getEnquiries(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) Long assignedAdminId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                EnquiryService.safeSort(sortBy, sortDir));

        PagedResponse<EnquiryResponseDto> response = enquiryService.getEnquiries(
                search, status, service, assignedAdminId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/filter-options")
    @Operation(summary = "Enquiry filter options", description = "Statuses and service names available for filtering")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getFilterOptions() {
        List<String> statuses = java.util.Arrays.stream(com.jovensbuzz.enquiry.entity.EnquiryStatus.values())
                .map(Enum::name).toList();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "statuses", statuses,
                "services", enquiryService.getServiceFilterOptions())));
    }

    @GetMapping("/deleted")
    @Operation(summary = "List deleted enquiries", description = "Soft-deleted enquiries (PERM_ENQUIRIES_DELETE); owners can delete them permanently")
    public ResponseEntity<ApiResponse<List<DeletedEnquiryDto>>> getDeleted() {
        return ResponseEntity.ok(ApiResponse.success(enquiryService.getDeletedEnquiries()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an enquiry", description = "Soft delete: hidden from lists, analytics and exports; moves to Deleted enquiries; audited")
    public ResponseEntity<ApiResponse<Void>> deleteEnquiry(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        enquiryService.deleteEnquiry(id, username(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Enquiry deleted", null));
    }

    @DeleteMapping("/deleted/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Permanently delete a deleted enquiry",
            description = "Owner only. Removes an enquiry that is already in Deleted enquiries; cannot be undone; audited")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteEnquiry(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        enquiryService.permanentlyDeleteEnquiry(id, username(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Enquiry permanently deleted", null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get enquiry details", description = "Retrieves complete enquiry with notes, status history, and sync state")
    public ResponseEntity<ApiResponse<EnquiryDetailResponseDto>> getEnquiryById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(enquiryService.getEnquiryById(id)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update enquiry status", description = "Validates the transition, records history and audit log")
    public ResponseEntity<ApiResponse<EnquiryDetailResponseDto>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody EnquiryStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        EnquiryDetailResponseDto response = enquiryService.updateStatus(id, request, username(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Status updated successfully", response));
    }

    @PatchMapping("/{id}/assignment")
    @Operation(summary = "Assign enquiry", description = "Assigns the enquiry to an administrator, or unassigns with adminId=null")
    public ResponseEntity<ApiResponse<EnquiryDetailResponseDto>> assign(
            @PathVariable Long id,
            @RequestBody EnquiryAssignmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        EnquiryDetailResponseDto response = enquiryService.assign(id, request, username(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Assignment updated", response));
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "List internal notes", description = "Internal notes for an enquiry, newest first")
    public ResponseEntity<ApiResponse<List<EnquiryNoteResponseDto>>> getNotes(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(enquiryService.getNotes(id)));
    }

    @PostMapping("/{id}/notes")
    @Operation(summary = "Add internal note", description = "Adds an administrative note to an enquiry")
    public ResponseEntity<ApiResponse<EnquiryNoteResponseDto>> addNote(
            @PathVariable Long id,
            @Valid @RequestBody EnquiryNoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        EnquiryNoteResponseDto response = enquiryService.addNote(id, request, username(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Note added successfully", response));
    }

    @GetMapping("/{id}/sync-status")
    @Operation(summary = "Google Sheets sync status", description = "Current synchronisation state for the enquiry")
    public ResponseEntity<ApiResponse<SyncStatusDto>> getSyncStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(enquiryService.getSyncStatus(id)));
    }

    @PostMapping("/{id}/retry-sync")
    @Operation(summary = "Retry Google Sheets sync", description = "Re-queues and immediately attempts synchronisation for this enquiry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retrySync(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean success = enquiryService.retrySync(id, username(userDetails));
        return ResponseEntity.ok(ApiResponse.success(
                success ? "Synchronization succeeded" : "Synchronization attempt failed; it will be retried automatically",
                Collections.singletonMap("synced", success)
        ));
    }

    @GetMapping("/{id}/notifications")
    @Operation(summary = "Email notification status", description = "Admin notification and client confirmation email delivery state")
    public ResponseEntity<ApiResponse<EmailNotificationOverviewDto>> getNotifications(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(emailNotificationService.getForEnquiry(id)));
    }

    @PostMapping("/{id}/notifications/retry")
    @Operation(summary = "Retry email notifications", description = "Re-queues and attempts any unsent emails for the enquiry; sent emails are never re-sent")
    public ResponseEntity<ApiResponse<EmailNotificationOverviewDto>> retryNotifications(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        EmailNotificationOverviewDto result = emailNotificationService.retry(id, username(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Email notifications re-queued", result));
    }

    @GetMapping("/export")
    @Operation(summary = "Export enquiries to CSV", description = "Downloads the filtered enquiries as CSV (max 10,000 rows)")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) Long assignedAdminId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @AuthenticationPrincipal UserDetails userDetails) {
        byte[] csvBytes = enquiryService.exportToCsv(
                search, status, service, assignedAdminId, startDate, endDate, username(userDetails));
        String filename = "jovens-buzz-enquiries-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }

    private static String username(UserDetails userDetails) {
        return userDetails != null ? userDetails.getUsername() : null;
    }
}
