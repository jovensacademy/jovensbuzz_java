package com.jovensbuzz.enquiry.dto;

import jakarta.validation.constraints.NotBlank;

public class EnquiryStatusUpdateRequest {

    @NotBlank(message = "New status is required")
    private String status;

    private String note;

    public EnquiryStatusUpdateRequest() {}

    public EnquiryStatusUpdateRequest(String status, String note) {
        this.status = status;
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
