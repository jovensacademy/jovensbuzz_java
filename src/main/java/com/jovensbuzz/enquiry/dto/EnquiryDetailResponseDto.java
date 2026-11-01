package com.jovensbuzz.enquiry.dto;

import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EnquiryDetailResponseDto {
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
    private boolean privacyConsent;
    private String privacyPolicyVersion;
    private Instant createdAt;
    private Instant updatedAt;

    // Sync Details
    private String syncStatus;
    private int syncAttemptCount;
    private String syncLastError;
    private Instant syncNextRetryAt;
    private Long assignedAdminId;
    private String assignedAdminName;
    private Instant syncUpdatedAt;
    private String phoneCountry;

    // Associated collections
    private List<EnquiryNoteResponseDto> notes = new ArrayList<>();
    private List<EnquiryStatusHistoryDto> statusHistory = new ArrayList<>();

    public EnquiryDetailResponseDto() {}

    public static EnquiryDetailResponseDto fromEntity(
            Enquiry entity,
            IntegrationSyncJob syncJob,
            List<EnquiryNoteResponseDto> notes,
            List<EnquiryStatusHistoryDto> history) {

        EnquiryDetailResponseDto dto = new EnquiryDetailResponseDto();
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
        dto.setPrivacyConsent(entity.isPrivacyConsent());
        dto.setPrivacyPolicyVersion(entity.getPrivacyPolicyVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        if (entity.getAssignedAdmin() != null) {
            dto.setAssignedAdminId(entity.getAssignedAdmin().getId());
            dto.setAssignedAdminName(entity.getAssignedAdmin().getDisplayName());
        }
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (syncJob != null) {
            dto.setSyncStatus(syncJob.getStatus().name());
            dto.setSyncAttemptCount(syncJob.getAttemptCount());
            dto.setSyncLastError(syncJob.getLastError());
            dto.setSyncNextRetryAt(syncJob.getNextRetryAt());
            dto.setSyncUpdatedAt(syncJob.getUpdatedAt());
        } else {
            dto.setSyncStatus("UNKNOWN");
        }

        dto.setNotes(notes != null ? notes : new ArrayList<>());
        dto.setStatusHistory(history != null ? history : new ArrayList<>());
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

    public boolean isPrivacyConsent() {
        return privacyConsent;
    }

    public void setPrivacyConsent(boolean privacyConsent) {
        this.privacyConsent = privacyConsent;
    }

    public String getPrivacyPolicyVersion() {
        return privacyPolicyVersion;
    }

    public void setPrivacyPolicyVersion(String privacyPolicyVersion) {
        this.privacyPolicyVersion = privacyPolicyVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public int getSyncAttemptCount() {
        return syncAttemptCount;
    }

    public void setSyncAttemptCount(int syncAttemptCount) {
        this.syncAttemptCount = syncAttemptCount;
    }

    public String getSyncLastError() {
        return syncLastError;
    }

    public void setSyncLastError(String syncLastError) {
        this.syncLastError = syncLastError;
    }

    public Instant getSyncNextRetryAt() {
        return syncNextRetryAt;
    }

    public void setSyncNextRetryAt(Instant syncNextRetryAt) {
        this.syncNextRetryAt = syncNextRetryAt;
    }

    public List<EnquiryNoteResponseDto> getNotes() {
        return notes;
    }

    public void setNotes(List<EnquiryNoteResponseDto> notes) {
        this.notes = notes;
    }

    public List<EnquiryStatusHistoryDto> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<EnquiryStatusHistoryDto> statusHistory) {
        this.statusHistory = statusHistory;
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

    public Instant getSyncUpdatedAt() {
        return syncUpdatedAt;
    }

    public void setSyncUpdatedAt(Instant syncUpdatedAt) {
        this.syncUpdatedAt = syncUpdatedAt;
    }

    public String getPhoneCountry() {
        return phoneCountry;
    }

    public void setPhoneCountry(String phoneCountry) {
        this.phoneCountry = phoneCountry;
    }
}
