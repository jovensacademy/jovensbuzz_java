package com.jovensbuzz.integration.email.service;

import com.jovensbuzz.integration.email.entity.EmailNotificationStatus;
import com.jovensbuzz.integration.email.repository.EmailNotificationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Drives email delivery: immediately after an enquiry commits (async, so the visitor never waits
 * for SMTP), and periodically for retries, stuck jobs, and jobs queued while SMTP was unconfigured.
 */
@Component
public class EmailNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationScheduler.class);
    private static final Duration STALE_PROCESSING = Duration.ofMinutes(10);

    private final EmailNotificationJobRepository jobRepository;
    private final EmailNotificationWorker worker;
    private final EmailSettings settings;
    private final TransactionTemplate tx;

    public EmailNotificationScheduler(EmailNotificationJobRepository jobRepository,
                                      EmailNotificationWorker worker,
                                      EmailSettings settings,
                                      PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.worker = worker;
        this.settings = settings;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobsQueued(EmailJobsQueuedEvent event) {
        event.jobIds().forEach(worker::process);
    }

    @Scheduled(fixedDelayString = "${app.mail.poll-interval-ms:60000}", initialDelayString = "${app.mail.initial-delay-ms:45000}")
    public void processDueJobs() {
        Instant now = Instant.now();
        Integer recovered = tx.execute(s -> jobRepository.recoverStale(
                EmailNotificationStatus.PROCESSING, EmailNotificationStatus.PENDING, now.minus(STALE_PROCESSING), now));
        if (recovered != null && recovered > 0) {
            log.warn("Recovered {} email jobs stuck in PROCESSING", recovered);
        }
        if (!settings.isSmtpConfigured()) {
            return; // jobs stay PENDING until SMTP is enabled and configured
        }
        List<Long> due = jobRepository.findDueJobIds(EmailNotificationStatus.PENDING, now);
        if (!due.isEmpty()) {
            log.info("Processing {} pending email notification(s)", due.size());
            due.forEach(worker::process);
        }
    }
}
