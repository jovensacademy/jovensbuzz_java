package com.jovensbuzz.faq.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.faq.dto.FaqItemDto;
import com.jovensbuzz.faq.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/faqs")
@Tag(name = "Public FAQs", description = "Public website FAQ list")
public class PublicFaqController {

    private final FaqService faqService;

    public PublicFaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    @GetMapping
    @Operation(summary = "Get published FAQs", description = "Retrieves all published FAQs ordered by display order")
    public ResponseEntity<ApiResponse<List<FaqItemDto>>> getPublishedFaqs() {
        List<FaqItemDto> faqs = faqService.getPublishedFaqs();
        return ResponseEntity.ok(ApiResponse.success(faqs));
    }
}
