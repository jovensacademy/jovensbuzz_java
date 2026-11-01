package com.jovensbuzz.sitecontent.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.sitecontent.service.SiteContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/site-content")
@Tag(name = "Public Site Content", description = "Approved public website copy and configuration")
public class PublicSiteContentController {

    private final SiteContentService siteContentService;

    public PublicSiteContentController(SiteContentService siteContentService) {
        this.siteContentService = siteContentService;
    }

    @GetMapping
    @Operation(summary = "Get public site content", description = "Returns key-value map of approved site copy, contact info, and links")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublicContent() {
        Map<String, String> content = siteContentService.getPublicContentMap();
        return ResponseEntity.ok(ApiResponse.success(content));
    }
}
