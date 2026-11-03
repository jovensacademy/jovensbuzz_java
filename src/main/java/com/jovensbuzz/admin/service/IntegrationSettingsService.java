package com.jovensbuzz.admin.service;

import com.jovensbuzz.admin.dto.IntegrationStatusDto;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.integration.googlesheets.GoogleSheetsService;
import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;
import com.jovensbuzz.integration.sync.entity.SyncStatus;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jovensbuzz.integration.email.entity.EmailNotificationStatus;
import com.jovensbuzz.integration.email.repository.EmailNotificationJobRepository;
import com.jovensbuzz.integration.email.service.EmailSettings;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Service
public class IntegrationSettingsService {

    private final GoogleSheetsService googleSheetsService;
    private final IntegrationSyncJobRepository syncJobRepository;
    private final AuditLogService auditLogService;
    private final EmailSettings emailSettings;
    private final EmailNotificationJobRepository emailJobRepository;

    public IntegrationSettingsService(GoogleSheetsService googleSheetsService,
                                      IntegrationSyncJobRepository syncJobRepository,
                                      AuditLogService auditLogService,
                                      EmailSettings emailSettings,
                                      EmailNotificationJobRepository emailJobRepository) {
        this.googleSheetsService = googleSheetsService;
        this.syncJobRepository = syncJobRepository;
        this.auditLogService = auditLogService;
        this.emailSettings = emailSettings;
        this.emailJobRepository = emailJobRepository;
    }

    /** SMTP health for the settings page. Host, sender and recipients are shown; credentials never are. */
    @Transactional(readOnly = true)
    public Map<String, Object> getEmailStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("smtpConfigured", emailSettings.isSmtpConfigured());
        status.put("smtpEnabled", emailSettings.isEnabled());
        status.put("disabledReason", emailSettings.disabledReason());
        status.put("smtpHost", emailSettings.getSmtpHost());
        status.put("smtpPort", emailSettings.getSmtpPort());
        status.put("smtpUsername", emailSettings.getSmtpUsername());
        status.put("smtpPasswordSet", emailSettings.isSmtpPasswordSet());
        status.put("fromEmail", emailSettings.getFromEmail());
        status.put("fromName", emailSettings.getFromName());
        status.put("adminNotificationEmail", emailSettings.getAdminNotificationEmail());
        status.put("clientSupportEmail", emailSettings.getClientSupportEmail());
        status.put("responseTime", emailSettings.getResponseTime());
        Map<EmailNotificationStatus, Long> jobs = emailJobRepository.countsByStatus();
        status.put("pending", jobs.getOrDefault(EmailNotificationStatus.PENDING, 0L)
                + jobs.getOrDefault(EmailNotificationStatus.PROCESSING, 0L));
        status.put("sent", jobs.getOrDefault(EmailNotificationStatus.SENT, 0L));
        status.put("failed", jobs.getOrDefault(EmailNotificationStatus.FAILED, 0L));
        return status;
    }

    @Transactional(readOnly = true)
    public IntegrationStatusDto getGoogleSheetsStatus() {
        IntegrationStatusDto dto = new IntegrationStatusDto();
        dto.setConfigured(googleSheetsService.isConfigured());
        dto.setSpreadsheetIdSet(googleSheetsService.isSpreadsheetIdSet());
        dto.setSpreadsheetIdHint(mask(googleSheetsService.getSpreadsheetId()));
        dto.setWorksheetName(googleSheetsService.getWorksheetName());
        dto.setCredentialsFileReadable(googleSheetsService.isCredentialsFileReadable());
        dto.setServiceAccountEmail(googleSheetsService.getServiceAccountEmail());
        Map<SyncStatus, Long> jobs = syncJobRepository.countsByStatus();
        dto.setPending(jobs.getOrDefault(SyncStatus.PENDING, 0L));
        dto.setProcessing(jobs.getOrDefault(SyncStatus.PROCESSING, 0L));
        dto.setRetrying(jobs.getOrDefault(SyncStatus.RETRYING, 0L));
        dto.setFailed(jobs.getOrDefault(SyncStatus.FAILED, 0L));
        dto.setSucceeded(jobs.getOrDefault(SyncStatus.SUCCESS, 0L));
        return dto;
    }

    @Transactional
    public int requeueFailed(String username) {
        List<IntegrationSyncJob> failed = syncJobRepository.findByStatus(SyncStatus.FAILED);
        Instant now = Instant.now();
        for (IntegrationSyncJob job : failed) {
            job.setStatus(SyncStatus.RETRYING);
            job.setAttemptCount(0);
            job.setNextRetryAt(now);
        }
        syncJobRepository.saveAll(failed);
        auditLogService.logAction(username, "REQUEUE_FAILED_SYNCS", "INTEGRATION", "GOOGLE_SHEETS",
                "Re-queued " + failed.size() + " failed job(s)");
        return failed.size();
    }

    private static String mask(String id) {
        if (id == null || id.isBlank()) return null;
        return id.length() <= 6 ? "******" : "…" + id.substring(id.length() - 6);
    }
}
