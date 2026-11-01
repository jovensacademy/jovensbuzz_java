package com.jovensbuzz.enquiry.dto;

/**
 * Assigns an enquiry to an administrator; a null adminId unassigns it.
 */
public class EnquiryAssignmentRequest {

    private Long adminId;

    public EnquiryAssignmentRequest() {}

    public EnquiryAssignmentRequest(Long adminId) {
        this.adminId = adminId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }
}
