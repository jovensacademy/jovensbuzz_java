package com.jovensbuzz.enquiry.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.enquiry.service.EnquiryExcelExportService;
import com.jovensbuzz.enquiry.service.EnquiryExcelImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * Excel export (PERM_ENQUIRIES_EXPORT) and import (PERM_ENQUIRIES_IMPORT); see SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/enquiries")
@Tag(name = "Admin Enquiry Excel", description = "Filter-aware Excel export and validated Excel import")
@SecurityRequirement(name = "bearerAuth")
public class AdminEnquiryExcelController {

    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EnquiryExcelExportService exportService;
    private final EnquiryExcelImportService importService;

    public AdminEnquiryExcelController(EnquiryExcelExportService exportService, EnquiryExcelImportService importService) {
        this.exportService = exportService;
        this.importService = importService;
    }

    @GetMapping("/export/excel")
    @Operation(summary = "Export enquiries to Excel",
            description = "Same filters and sort as the list; exports every matching record (not just one page), in the user's time zone")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) Long assignedAdminId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal UserDetails user) {
        EnquiryExcelExportService.ExportResult result = exportService.export(
                new EnquiryExcelExportService.ExportFilters(search, status, service, assignedAdminId, startDate, endDate, sortBy, sortDir),
                user != null ? user.getUsername() : null);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .header("X-Export-Count", String.valueOf(result.rows()))
                .contentType(XLSX)
                .body(result.content());
    }

    @GetMapping("/import/template")
    @Operation(summary = "Download the Excel import template")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jovens-buzz-enquiry-import-template.xlsx\"")
                .contentType(XLSX)
                .body(importService.template());
    }

    @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validate an Excel file", description = "Parses and validates every row and flags duplicates; saves nothing")
    public ResponseEntity<ApiResponse<EnquiryExcelImportService.Preview>> preview(@RequestParam("file") MultipartFile file,
                                                                                 @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(importService.preview(file, user != null ? user.getUsername() : null)));
    }

    @PostMapping("/import/validate")
    @Operation(summary = "Re-validate corrected rows", description = "Same checks as preview, for rows edited in the browser; saves nothing")
    public ResponseEntity<ApiResponse<EnquiryExcelImportService.Preview>> validate(@RequestBody EnquiryExcelImportService.CommitRequest request,
                                                                                  @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(importService.validateRows(request.rows(), request.filename(),
                user != null ? user.getUsername() : null)));
    }

    @PostMapping("/import/commit")
    @Operation(summary = "Import validated rows", description = "All rows are saved in one transaction, or none are")
    public ResponseEntity<ApiResponse<EnquiryExcelImportService.Summary>> commit(@RequestBody EnquiryExcelImportService.CommitRequest request,
                                                                                @AuthenticationPrincipal UserDetails user) {
        EnquiryExcelImportService.Summary summary = importService.commit(request, user != null ? user.getUsername() : null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Imported " + summary.imported() + " enquiries", summary));
    }

    @ExceptionHandler(EnquiryExcelImportService.ImportRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> rejected(EnquiryExcelImportService.ImportRejectedException ex) {
        List<EnquiryExcelImportService.RowResult> rows = ex.getRows();
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), rows));
    }
}
