package com.jovensbuzz.integration.email.service;

import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.ResourceNotFoundException;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.integration.email.dto.EmailNotificationDto;
import com.jovensbuzz.integration.email.dto.EmailNotificationOverviewDto;
import com.jovensbuzz.integration.email.entity.EmailNotificationJob;
import com.jovensbuzz.integration.email.entity.EmailNotificationStatus;
import com.jovensbuzz.integration.email.entity.EmailNotificationType;
import com.jovensbuzz.integration.email.repository.EmailNotificationJobRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates and coordinates enquiry emails. Jobs are written in the caller's transaction (the
 * enquiry save), so an email is queued if and only if the enquiry is persisted; delivery happens
 * asynchronously after commit and never blocks the public form request.
 */
@Service
public class EmailNotificationService {

    private final EmailNotificationJobRepository jobRepository;
    private final EnquiryRepository enquiryRepository;
    private final EmailNotificationWorker worker;
    private final EmailSettings settings;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public EmailNotificationService(EmailNotificationJobRepository jobRepository,
                                    EnquiryRepository enquiryRepository,
                                    EmailNotificationWorker worker,
                                    EmailSettings settings,
                                    AuditLogService auditLogService,
                                    ApplicationEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.enquiryRepository = enquiryRepository;
        this.worker = worker;
        this.settings = settings;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Queues the admin notification and the client confirmation for a newly saved enquiry.
     * Must be called inside the enquiry's transaction.
     */
    @Transactional
    public List<Long> queueForNewEnquiry(Enquiry enquiry) {
        List<Long> ids = new ArrayList<>();
        ids.add(queue(enquiry, EmailNotificationType.ADMIN_ENQUIRY_NOTIFICATION, null,
                EmailTemplateService.ADMIN_SUBJECT));
        ids.add(queue(enquiry, EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION, enquiry.getEmail(),
                EmailTemplateService.CLIENT_SUBJECT));
        eventPublisher.publishEvent(new EmailJobsQueuedEvent(ids));
        return ids;
    }

    /** Idempotent: an existing job for the same enquiry and type is reused (DB unique key backs this). */
    private Long queue(Enquiry enquiry, EmailNotificationType type, String recipient, String subject) {
        if (enquiry.getId() != null) {
            var existing = jobRepository.findByEnquiryIdAndNotificationType(enquiry.getId(), type);
            if (existing.isPresent()) return existing.get().getId();
        }
        return jobRepository.save(new EmailNotificationJob(enquiry, type, recipient, subject)).getId();
    }

    @Transactional(readOnly = true)
    public EmailNotificationOverviewDto getForEnquiry(Long enquiryId) {
        if (!enquiryRepository.existsById(enquiryId)) {
            throw new ResourceNotFoundException("Enquiry", "id", enquiryId);
        }
        List<EmailNotificationDto> jobs = jobRepository.findByEnquiryIdOrderByNotificationTypeAsc(enquiryId).stream()
                .map(EmailNotificationDto::fromEntity)
                .toList();
        return new EmailNotificationOverviewDto(settings.isSmtpConfigured(), settings.isAdminRecipientConfigured(), jobs);
    }

    /**
     * Re-queues every unsent email for the enquiry (creating missing jobs for older enquiries) and
     * attempts delivery now. SENT jobs are never touched, so this cannot produce duplicates.
     * Intentionally not @Transactional: SMTP calls must not hold a database transaction.
     */
    public EmailNotificationOverviewDto retry(Long enquiryId, String username) {
        Enquiry enquiry = enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry", "id", enquiryId));

        List<Long> toProcess = new ArrayList<>();
        for (EmailNotificationType type : EmailNotificationType.values()) {
            EmailNotificationJob job = jobRepository.findByEnquiryIdAndNotificationType(enquiryId, type)
                    .orElseGet(() -> jobRepository.save(new EmailNotificationJob(enquiry, type,
                            type == EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION ? enquiry.getEmail() : null,
                            type == EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION
                                    ? EmailTemplateService.CLIENT_SUBJECT : EmailTemplateService.ADMIN_SUBJECT)));
            if (job.getStatus() == EmailNotificationStatus.SENT || job.getStatus() == EmailNotificationStatus.PROCESSING) {
                continue;
            }
            job.setStatus(EmailNotificationStatus.PENDING);
            job.setAttemptCount(0);
            job.setNextRetryAt(null);
            jobRepository.saveAndFlush(job);
            toProcess.add(job.getId());
        }

        auditLogService.logAction(username, "RETRY_EMAIL", "ENQUIRY", String.valueOf(enquiryId),
                "Re-queued " + toProcess.size() + " email notification(s)");
        toProcess.forEach(worker::process);
        return getForEnquiry(enquiryId);
    }
}
