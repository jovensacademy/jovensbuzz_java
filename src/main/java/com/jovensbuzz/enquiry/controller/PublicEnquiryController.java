package com.jovensbuzz.enquiry.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.enquiry.dto.EnquiryCreateRequest;
import com.jovensbuzz.enquiry.dto.EnquirySubmissionResponse;
import com.jovensbuzz.enquiry.service.EnquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enquiries")
@Tag(name = "Public Enquiry", description = "Public website enquiry submission")
public class PublicEnquiryController {

    private final EnquiryService enquiryService;

    public PublicEnquiryController(EnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    @PostMapping
    @Operation(summary = "Submit an enquiry",
            description = "Stores the enquiry in MySQL and queues Google Sheets synchronisation. "
                    + "Identical resubmissions within 15 minutes return the original reference (200).")
    public ResponseEntity<ApiResponse<EnquirySubmissionResponse>> submitEnquiry(
            @Valid @RequestBody EnquiryCreateRequest request) {
        EnquirySubmissionResponse receipt = enquiryService.createEnquiry(request);
        String message = receipt.isDuplicate()
                ? "We already have this enquiry. Our team will contact you shortly."
                : "Thank you! Your enquiry has been received. Our team will contact you shortly.";
        return ResponseEntity.status(receipt.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(message, receipt));
    }
}
