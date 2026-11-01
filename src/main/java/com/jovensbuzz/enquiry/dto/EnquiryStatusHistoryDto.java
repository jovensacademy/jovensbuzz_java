package com.jovensbuzz.enquiry.dto;

import com.jovensbuzz.enquiry.entity.EnquiryStatusHistory;
import java.time.Instant;

public class EnquiryStatusHistoryDto {
    private Long id;
    private String changedBy;
    private String previousStatus;
    private String newStatus;
    private Instant changedAt;

    public EnquiryStatusHistoryDto() {}

    public static EnquiryStatusHistoryDto fromEntity(EnquiryStatusHistory history) {
        EnquiryStatusHistoryDto dto = new EnquiryStatusHistoryDto();
        dto.setId(history.getId());
        dto.setChangedBy(history.getChangedBy());
        dto.setPreviousStatus(history.getPreviousStatus());
        dto.setNewStatus(history.getNewStatus());
        dto.setChangedAt(history.getChangedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }
}
