package com.jovensbuzz.faq.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.faq.dto.FaqItemDto;
import com.jovensbuzz.faq.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/faqs")
@Tag(name = "Admin FAQs", description = "Administration of website FAQs")
@SecurityRequirement(name = "bearerAuth")
public class AdminFaqController {

    private final FaqService faqService;

    public AdminFaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    @GetMapping
    @Operation(summary = "Get all FAQs", description = "Retrieves all FAQs including unpublished drafts")
    public ResponseEntity<ApiResponse<List<FaqItemDto>>> getAllFaqs() {
        List<FaqItemDto> faqs = faqService.getAllFaqs();
        return ResponseEntity.ok(ApiResponse.success(faqs));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get FAQ by ID", description = "Retrieves a single FAQ item")
    public ResponseEntity<ApiResponse<FaqItemDto>> getFaqById(@PathVariable Long id) {
        FaqItemDto faq = faqService.getFaqById(id);
        return ResponseEntity.ok(ApiResponse.success(faq));
    }

    @PostMapping
    @Operation(summary = "Create FAQ", description = "Creates a new FAQ")
    public ResponseEntity<ApiResponse<FaqItemDto>> createFaq(
            @Valid @RequestBody FaqItemDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "ADMIN";
        FaqItemDto created = faqService.createFaq(dto, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("FAQ created successfully", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update FAQ", description = "Updates an existing FAQ")
    public ResponseEntity<ApiResponse<FaqItemDto>> updateFaq(
            @PathVariable Long id,
            @Valid @RequestBody FaqItemDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "ADMIN";
        FaqItemDto updated = faqService.updateFaq(id, dto, username);
        return ResponseEntity.ok(ApiResponse.success("FAQ updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete FAQ", description = "Deletes a FAQ by ID")
    public ResponseEntity<ApiResponse<Void>> deleteFaq(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "ADMIN";
        faqService.deleteFaq(id, username);
        return ResponseEntity.ok(ApiResponse.success("FAQ deleted successfully", null));
    }
}
