package com.jovensbuzz.enquiry.dto;

import com.jovensbuzz.enquiry.entity.Enquiry;
import java.time.Instant;

public class EnquiryResponseDto {
    private Long id;
    private String publicReference;
    private String fullName;
    private String email;
    private String phone;
    private String companyName;
    private String serviceInterest;
    private String budget;
    private String message;
    private String status;
    private String source;
    private Instant createdAt;
    private String syncStatus;
    private Long assignedAdminId;
    private String assignedAdminName;
    private String phoneCountry;

    public EnquiryResponseDto() {}

    public static EnquiryResponseDto fromEntity(Enquiry entity, String syncStatus) {
        EnquiryResponseDto dto = new EnquiryResponseDto();
        dto.setId(entity.getId());
        dto.setPublicReference(entity.getPublicReference());
        dto.setFullName(entity.getFullName());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setPhoneCountry(entity.getPhoneCountry());
        dto.setCompanyName(entity.getCompanyName());
        dto.setServiceInterest(entity.getServiceInterest());
        dto.setBudget(entity.getBudget());
        dto.setMessage(entity.getMessage());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : "NEW");
        dto.setSource(entity.getSource());
        dto.setCreatedAt(entity.getCreatedAt());
        if (entity.getAssignedAdmin() != null) {
            dto.setAssignedAdminId(entity.getAssignedAdmin().getId());
            dto.setAssignedAdminName(entity.getAssignedAdmin().getDisplayName());
        }
        dto.setSyncStatus(syncStatus != null ? syncStatus : "UNKNOWN");
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPublicReference() {
        return publicReference;
    }

    public void setPublicReference(String publicReference) {
        this.publicReference = publicReference;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getServiceInterest() {
        return serviceInterest;
    }

    public void setServiceInterest(String serviceInterest) {
        this.serviceInterest = serviceInterest;
    }

    public String getBudget() {
        return budget;
    }

    public void setBudget(String budget) {
        this.budget = budget;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Long getAssignedAdminId() {
        return assignedAdminId;
    }

    public void setAssignedAdminId(Long assignedAdminId) {
        this.assignedAdminId = assignedAdminId;
    }

    public String getAssignedAdminName() {
        return assignedAdminName;
    }

    public void setAssignedAdminName(String assignedAdminName) {
        this.assignedAdminName = assignedAdminName;
    }

    public String getPhoneCountry() {
        return phoneCountry;
    }

    public void setPhoneCountry(String phoneCountry) {
        this.phoneCountry = phoneCountry;
    }
}
