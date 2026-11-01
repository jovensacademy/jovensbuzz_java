package com.jovensbuzz.integration.sync.service;

import com.jovensbuzz.integration.googlesheets.GoogleSheetsService;
import com.jovensbuzz.integration.googlesheets.SheetRow;
import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;
import com.jovensbuzz.integration.sync.entity.SyncStatus;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Durable Google Sheets synchronisation worker.
 *
 * Flow per job: (1) atomically claim it (PENDING/RETRYING/FAILED -> PROCESSING) in a short
 * transaction and snapshot the row, (2) call Google with no transaction open, (3) record the
 * outcome in a second short transaction. Retries use exponential backoff; the sheet itself is
 * checked for the reference before appending, so retries never duplicate rows.
 */
@Service
public class SyncJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(SyncJobProcessor.class);
    public static final int MAX_ATTEMPTS = 6;
    public static final String NOT_CONFIGURED_ERROR =
            "Google Sheets is not configured (missing web app URL or credentials file)";
    // Shared by every wording this error has had, e.g. "(missing spreadsheet ID or credentials file)"
    static final String NOT_CONFIGURED_PREFIX = "Google Sheets is not configured";
    private static final EnumSet<SyncStatus> CLAIMABLE =
            EnumSet.of(SyncStatus.PENDING, SyncStatus.RETRYING, SyncStatus.FAILED);
    // Freshly submitted jobs are handled by the post-commit worker; the scheduler is the safety net
    private static final Duration PENDING_GRACE = Duration.ofSeconds(30);
    private static final Duration STALE_PROCESSING = Duration.ofMinutes(10);

    private final IntegrationSyncJobRepository syncJobRepository;
    private final GoogleSheetsService googleSheetsService;
    private final TransactionTemplate transactionTemplate;

    public SyncJobProcessor(
            IntegrationSyncJobRepository syncJobRepository,
            GoogleSheetsService googleSheetsService,
            PlatformTransactionManager transactionManager) {
        this.syncJobRepository = syncJobRepository;
        this.googleSheetsService = googleSheetsService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Processes one job. Returns true when the enquiry is (now or already) in the spreadsheet.
     */
    public boolean processSingleJob(Long syncJobId) {
        SheetRow row = transactionTemplate.execute(status -> claimAndSnapshot(syncJobId));
        if (row == null) {
            // Not claimable: already synced, being processed elsewhere, or missing
            return syncJobRepository.findById(syncJobId)
                    .map(job -> job.getStatus() == SyncStatus.SUCCESS)
                    .orElse(false);
        }

        if (!googleSheetsService.isConfigured()) {
            transactionTemplate.executeWithoutResult(status -> recordNotConfigured(syncJobId));
            log.info("Google Sheets integration unconfigured. Sync job {} marked as FAILED.", syncJobId);
            return false;
        }

        try {
            String range = googleSheetsService.appendIfAbsent(row);
            transactionTemplate.executeWithoutResult(status -> recordSuccess(syncJobId, range));
            log.info("Synchronized enquiry '{}' to Google Sheets (job {})", row.reference(), syncJobId);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to sync enquiry '{}' to Google Sheets: {}", row.reference(), safeErrorMessage(ex));
            transactionTemplate.executeWithoutResult(status -> recordFailure(syncJobId, ex));
            return false;
        }
    }

    private SheetRow claimAndSnapshot(Long syncJobId) {
        int claimed = syncJobRepository.claim(syncJobId, CLAIMABLE, SyncStatus.PROCESSING, Instant.now());
        if (claimed == 0) {
            return null;
        }
        IntegrationSyncJob job = syncJobRepository.findById(syncJobId).orElse(null);
        if (job == null) {
            return null;
        }
        return SheetRow.fromEnquiry(job.getEnquiry());
    }

    private void recordNotConfigured(Long syncJobId) {
        syncJobRepository.findById(syncJobId).ifPresent(job -> {
            job.setStatus(SyncStatus.FAILED);
            job.setLastError(NOT_CONFIGURED_ERROR);
            job.setNextRetryAt(null);
            syncJobRepository.save(job);
        });
    }

    private void recordSuccess(Long syncJobId, String range) {
        syncJobRepository.findById(syncJobId).ifPresent(job -> {
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setStatus(SyncStatus.SUCCESS);
            job.setExternalRef(range);
            job.setLastError(null);
            job.setNextRetryAt(null);
            syncJobRepository.save(job);
        });
    }

    private void recordFailure(Long syncJobId, Exception ex) {
        syncJobRepository.findById(syncJobId).ifPresent(job -> {
            job.setAttemptCount(job.getAttemptCount() + 1);
            if (job.getAttemptCount() >= MAX_ATTEMPTS) {
                job.setStatus(SyncStatus.FAILED);
                job.setNextRetryAt(null);
            } else {
                job.setStatus(SyncStatus.RETRYING);
                job.setNextRetryAt(Instant.now().plus(backoff(job.getAttemptCount())));
            }
            job.setLastError(safeErrorMessage(ex));
            syncJobRepository.save(job);
        });
    }

    /**
     * Exponential backoff: 1, 2, 4, 8, 16 ... minutes, capped at 2 hours.
     */
    static Duration backoff(int attempt) {
        long minutes = Math.min(120L, 1L << Math.max(0, Math.min(attempt - 1, 10)));
        return Duration.ofMinutes(minutes);
    }

    /**
     * Keeps the error useful for admins while never echoing credentials or long payloads.
     */
    private static String safeErrorMessage(Exception ex) {
        String message = ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
        message = message.replaceAll("(?i)(access_token|refresh_token|private_key|client_secret|token|secret)[^,}\\s]*", "$1=[redacted]");
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    @Scheduled(fixedDelayString = "${app.sync.poll-interval-ms:60000}", initialDelayString = "${app.sync.initial-delay-ms:30000}")
    public void processPendingRetries() {
        Instant now = Instant.now();
        Integer recovered = transactionTemplate.execute(status -> syncJobRepository.recoverStale(
                SyncStatus.PROCESSING, SyncStatus.RETRYING, now.minus(STALE_PROCESSING), now));
        if (recovered != null && recovered > 0) {
            log.warn("Recovered {} sync jobs stuck in PROCESSING", recovered);
        }

        List<Long> dueJobIds = syncJobRepository.findDueJobIds(
                SyncStatus.PENDING, SyncStatus.RETRYING, now.minus(PENDING_GRACE), now);

        // Once Sheets is configured, catch up on jobs that failed only because it was not
        if (googleSheetsService.isConfigured()) {
            List<Long> waiting = syncJobRepository.findIdsByStatusAndLastErrorStartingWith(SyncStatus.FAILED, NOT_CONFIGURED_PREFIX);
            if (!waiting.isEmpty()) {
                dueJobIds = new java.util.ArrayList<>(dueJobIds);
                dueJobIds.addAll(waiting);
            }
        }

        if (!dueJobIds.isEmpty()) {
            log.info("Processing {} pending/due integration sync jobs", dueJobIds.size());
            for (Long jobId : dueJobIds) {
                processSingleJob(jobId);
            }
        }
    }
}
