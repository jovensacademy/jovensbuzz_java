package com.jovensbuzz.integration.email.service;

import com.jovensbuzz.integration.email.entity.EmailNotificationJob;
import com.jovensbuzz.integration.email.entity.EmailNotificationStatus;
import com.jovensbuzz.integration.email.entity.EmailNotificationType;
import com.jovensbuzz.integration.email.repository.EmailNotificationJobRepository;
import com.jovensbuzz.integration.email.service.EmailTemplateService.EmailContent;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Delivers one email job: (1) atomically claim it and render the email in a short transaction,
 * (2) talk to SMTP with no transaction open, (3) record the outcome. A SENT job is never claimable
 * again, so retries cannot send duplicates.
 */
@Service
public class EmailNotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationWorker.class);
    public static final int MAX_ATTEMPTS = 6;
    public static final String ADMIN_RECIPIENT_NOT_CONFIGURED = "ADMIN_NOTIFICATION_EMAIL is not configured";

    private final EmailNotificationJobRepository jobRepository;
    private final EmailTemplateService templates;
    private final EmailSettings settings;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final TransactionTemplate tx;

    public EmailNotificationWorker(EmailNotificationJobRepository jobRepository,
                                   EmailTemplateService templates,
                                   EmailSettings settings,
                                   ObjectProvider<JavaMailSender> mailSender,
                                   PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.templates = templates;
        this.settings = settings;
        this.mailSender = mailSender;
        this.tx = new TransactionTemplate(transactionManager);
    }

    private record Prepared(String recipient, EmailContent content, EmailNotificationType type, String reference) {}

    /**
     * @return true when the email is (now or already) sent
     */
    public boolean process(Long jobId) {
        if (!settings.isSmtpConfigured()) {
            // Leave the job PENDING (no attempt consumed) and say why; the scheduler sends it once configured
            tx.executeWithoutResult(s -> jobRepository.findById(jobId)
                    .filter(job -> job.getStatus() == EmailNotificationStatus.PENDING)
                    .ifPresent(job -> {
                        job.setLastError(settings.disabledReason());
                        jobRepository.save(job);
                    }));
            return false;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("No JavaMailSender bean available; email job {} left pending", jobId);
            return false;
        }

        Prepared prepared = tx.execute(s -> claimAndRender(jobId));
        if (prepared == null) {
            return jobRepository.findById(jobId).map(j -> j.getStatus() == EmailNotificationStatus.SENT).orElse(false);
        }
        if (!StringUtils.hasText(prepared.recipient())) {
            tx.executeWithoutResult(s -> release(jobId, ADMIN_RECIPIENT_NOT_CONFIGURED));
            return false;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.getFromEmail(), settings.getFromName());
            helper.setTo(prepared.recipient());
            helper.setSubject(prepared.content().subject());
            helper.setText(prepared.content().text(), prepared.content().html()); // plain-text fallback + HTML
            if (prepared.type() == EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION
                    && StringUtils.hasText(settings.getClientSupportEmail())) {
                helper.setReplyTo(settings.getClientSupportEmail());
            }
            if (prepared.content().html().contains("cid:jovensBuzzLogo")) {
                org.springframework.core.io.ClassPathResource logo =
                        new org.springframework.core.io.ClassPathResource("static/images/logo-jovens-buzz.png");
                if (logo.exists()) {
                    helper.addInline("jovensBuzzLogo", logo, "image/png");
                }
            }
            sender.send(message);
            tx.executeWithoutResult(s -> recordSuccess(jobId));
            log.info("Sent {} for enquiry {} (job {})", prepared.type(), prepared.reference(), jobId);
            return true;
        } catch (Exception ex) {
            log.warn("Email job {} ({}) failed: {}", jobId, prepared.type(), ex.getMessage());
            tx.executeWithoutResult(s -> recordFailure(jobId, ex));
            return false;
        }
    }

    private Prepared claimAndRender(Long jobId) {
        int claimed = jobRepository.claim(jobId, EmailNotificationStatus.PENDING, EmailNotificationStatus.PROCESSING, Instant.now());
        if (claimed == 0) return null;
        EmailNotificationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return null;
        boolean admin = job.getNotificationType() == EmailNotificationType.ADMIN_ENQUIRY_NOTIFICATION;
        EmailContent content = admin ? templates.adminNotification(job.getEnquiry()) : templates.clientConfirmation(job.getEnquiry());
        String recipient = admin
                ? (StringUtils.hasText(job.getRecipientEmail()) ? job.getRecipientEmail() : settings.getAdminNotificationEmail())
                : job.getRecipientEmail();
        return new Prepared(recipient, content, job.getNotificationType(), job.getEnquiry().getPublicReference());
    }

    /** Back to PENDING without consuming an attempt (configuration problem, not a delivery failure). */
    private void release(Long jobId, String reason) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(EmailNotificationStatus.PENDING);
            job.setLastError(reason);
            job.setNextRetryAt(null);
            jobRepository.save(job);
        });
    }

    private void recordSuccess(Long jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(EmailNotificationStatus.SENT);
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setSentAt(Instant.now());
            job.setLastError(null);
            job.setNextRetryAt(null);
            jobRepository.save(job);
        });
    }

    private void recordFailure(Long jobId, Exception ex) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setLastError(safeError(ex));
            if (job.getAttemptCount() >= MAX_ATTEMPTS) {
                job.setStatus(EmailNotificationStatus.FAILED);
                job.setNextRetryAt(null);
            } else {
                job.setStatus(EmailNotificationStatus.PENDING);
                job.setNextRetryAt(Instant.now().plus(backoff(job.getAttemptCount())));
            }
            jobRepository.save(job);
        });
    }

    /** 1, 2, 4, 8, 16 … minutes, capped at 2 hours. */
    static Duration backoff(int attempt) {
        return Duration.ofMinutes(Math.min(120L, 1L << Math.max(0, Math.min(attempt - 1, 10))));
    }

    /** Short, credential-free description of the failure for admins. */
    static String safeError(Exception ex) {
        String message = ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
        message = message.replaceAll("(?i)(password|passwd|pwd|auth[a-z]*)\\s*[=:]\\s*\\S+", "$1=[redacted]");
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
