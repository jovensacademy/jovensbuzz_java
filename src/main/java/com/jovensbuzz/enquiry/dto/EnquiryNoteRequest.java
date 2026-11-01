package com.jovensbuzz.enquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EnquiryNoteRequest {

    @NotBlank(message = "Note content cannot be empty")
    @Size(max = 2000, message = "Note must not exceed 2000 characters")
    private String note;

    public EnquiryNoteRequest() {}

    public EnquiryNoteRequest(String note) {
        this.note = note;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
