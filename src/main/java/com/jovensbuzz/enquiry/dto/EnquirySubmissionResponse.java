package com.jovensbuzz.enquiry.dto;

import java.time.Instant;

/**
 * Receipt returned to anonymous website visitors. Deliberately excludes internal IDs and
 * the submitted personal data.
 */
public class EnquirySubmissionResponse {

    private String publicReference;
    private Instant submittedAt;
    private boolean duplicate;

    public EnquirySubmissionResponse() {}

    public EnquirySubmissionResponse(String publicReference, Instant submittedAt, boolean duplicate) {
        this.publicReference = publicReference;
        this.submittedAt = submittedAt;
        this.duplicate = duplicate;
    }

    public String getPublicReference() {
        return publicReference;
    }

    public void setPublicReference(String publicReference) {
        this.publicReference = publicReference;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }
}
