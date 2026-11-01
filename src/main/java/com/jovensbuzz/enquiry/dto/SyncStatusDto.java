package com.jovensbuzz.enquiry.dto;

import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;

import java.time.Instant;

/**
 * Admin-safe view of an enquiry's Google Sheets synchronisation job.
 */
public class SyncStatusDto {

    private String integrationType;
    private String status;
    private int attemptCount;
    private String lastError;
    private Instant nextRetryAt;
    private Instant updatedAt;
    private boolean retryable;

    public SyncStatusDto() {}

    public static SyncStatusDto fromEntity(IntegrationSyncJob job) {
        SyncStatusDto dto = new SyncStatusDto();
        dto.setIntegrationType(job.getIntegrationType());
        dto.setStatus(job.getStatus().name());
        dto.setAttemptCount(job.getAttemptCount());
        dto.setLastError(job.getLastError());
        dto.setNextRetryAt(job.getNextRetryAt());
        dto.setUpdatedAt(job.getUpdatedAt());
        dto.setRetryable(switch (job.getStatus()) {
            case FAILED, RETRYING, PENDING -> true;
            default -> false;
        });
        return dto;
    }

    public static SyncStatusDto none() {
        SyncStatusDto dto = new SyncStatusDto();
        dto.setIntegrationType("GOOGLE_SHEETS");
        dto.setStatus("NOT_QUEUED");
        dto.setRetryable(true);
        return dto;
    }

    public String getIntegrationType() {
        return integrationType;
    }

    public void setIntegrationType(String integrationType) {
        this.integrationType = integrationType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public void setRetryable(boolean retryable) {
        this.retryable = retryable;
    }
}
