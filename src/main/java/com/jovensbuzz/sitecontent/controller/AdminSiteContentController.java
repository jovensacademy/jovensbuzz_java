package com.jovensbuzz.sitecontent.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.sitecontent.dto.SiteContentDto;
import com.jovensbuzz.sitecontent.service.SiteContentService;
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
@RequestMapping("/api/v1/admin/content")
@Tag(name = "Admin Site Content", description = "Administration of website copy, contact info, and branding keys")
@SecurityRequirement(name = "bearerAuth")
public class AdminSiteContentController {

    private final SiteContentService siteContentService;

    public AdminSiteContentController(SiteContentService siteContentService) {
        this.siteContentService = siteContentService;
    }

    @GetMapping
    @Operation(summary = "Get all site content items", description = "Returns full list of editable site content keys and values")
    public ResponseEntity<ApiResponse<List<SiteContentDto>>> getAllContent() {
        List<SiteContentDto> list = siteContentService.getAllContent();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update content by key", description = "Updates a single content entry by key")
    public ResponseEntity<ApiResponse<SiteContentDto>> updateContentByKey(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        String value = body.getOrDefault("contentValue", "");
        String username = userDetails != null ? userDetails.getUsername() : "ADMIN";
        SiteContentDto updated = siteContentService.updateContent(key, value, username);
        return ResponseEntity.ok(ApiResponse.success("Content updated successfully", updated));
    }

    @PostMapping("/batch")
    @Operation(summary = "Batch update content", description = "Updates multiple content keys at once")
    public ResponseEntity<ApiResponse<Void>> batchUpdate(
            @RequestBody Map<String, String> updates,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails != null ? userDetails.getUsername() : "ADMIN";
        siteContentService.batchUpdate(updates, username);
        return ResponseEntity.ok(ApiResponse.success("Content batch updated successfully", null));
    }
}
