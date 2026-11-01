package com.jovensbuzz;

import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.integration.email.entity.EmailNotificationJob;
import com.jovensbuzz.integration.email.entity.EmailNotificationStatus;
import com.jovensbuzz.integration.email.entity.EmailNotificationType;
import com.jovensbuzz.integration.email.repository.EmailNotificationJobRepository;
import com.jovensbuzz.integration.email.service.EmailJobsQueuedEvent;
import com.jovensbuzz.integration.email.service.EmailNotificationService;
import com.jovensbuzz.integration.email.service.EmailNotificationWorker;
import com.jovensbuzz.integration.email.service.EmailSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailNotificationServiceTest {

    @Mock
    private EmailNotificationJobRepository jobRepository;
    @Mock
    private EnquiryRepository enquiryRepository;
    @Mock
    private EmailNotificationWorker worker;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EmailNotificationService service;
    private Enquiry enquiry;
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        EmailSettings settings = new EmailSettings("", "", "Jovens Buzz", "", "", "http://localhost:3000", "", "within 24 hours");
        service = new EmailNotificationService(jobRepository, enquiryRepository, worker, settings, auditLogService, eventPublisher);
        enquiry = new Enquiry();
        enquiry.setId(9L);
        enquiry.setEmail("client@example.com");
        when(enquiryRepository.findById(9L)).thenReturn(Optional.of(enquiry));
        when(enquiryRepository.existsById(9L)).thenReturn(true);
        when(jobRepository.save(any(EmailNotificationJob.class))).thenAnswer(inv -> {
            EmailNotificationJob job = inv.getArgument(0);
            if (job.getId() == null) job.setId(ids.incrementAndGet());
            return job;
        });
        when(jobRepository.saveAndFlush(any(EmailNotificationJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void newEnquiryQueuesAdminAndClientJobs_andPublishesAfterCommitEvent() {
        when(jobRepository.findByEnquiryIdAndNotificationType(eq(9L), any())).thenReturn(Optional.empty());

        List<Long> queued = service.queueForNewEnquiry(enquiry);

        ArgumentCaptor<EmailNotificationJob> jobs = ArgumentCaptor.forClass(EmailNotificationJob.class);
        verify(jobRepository, times(2)).save(jobs.capture());
        EmailNotificationJob admin = jobs.getAllValues().get(0);
        EmailNotificationJob client = jobs.getAllValues().get(1);
        assertEquals(EmailNotificationType.ADMIN_ENQUIRY_NOTIFICATION, admin.getNotificationType());
        assertNull(admin.getRecipientEmail(), "admin address is resolved from configuration at send time");
        assertEquals(EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION, client.getNotificationType());
        assertEquals("client@example.com", client.getRecipientEmail());
        assertEquals("Thank You for Contacting Jovens Buzz!", client.getSubject());
        assertEquals(EmailNotificationStatus.PENDING, client.getStatus());
        verify(eventPublisher).publishEvent(new EmailJobsQueuedEvent(queued));
        verifyNoInteractions(worker); // delivery never happens inside the request transaction
    }

    @Test
    void queueingIsIdempotentPerEnquiryAndType() {
        EmailNotificationJob existing = new EmailNotificationJob(enquiry, EmailNotificationType.ADMIN_ENQUIRY_NOTIFICATION, null, "s");
        existing.setId(5L);
        when(jobRepository.findByEnquiryIdAndNotificationType(9L, EmailNotificationType.ADMIN_ENQUIRY_NOTIFICATION))
                .thenReturn(Optional.of(existing));
        when(jobRepository.findByEnquiryIdAndNotificationType(9L, EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION))
                .thenReturn(Optional.empty());

        List<Long> queued = service.queueForNewEnquiry(enquiry);

        assertEquals(5L, queued.get(0));
        verify(jobRepository, times(1)).save(any());
    }

    @Test
    void retryRequeuesUnsentJobsOnly() {
        EmailNotificationJob sent = new EmailNotificationJob(enquiry, EmailNotificationType.ADMIN_ENQUIRY_NOTIFICATION, null, "s");
        sent.setId(1L);
        sent.setStatus(EmailNotificationStatus.SENT);
        EmailNotificationJob failed = new EmailNotificationJob(enquiry, EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION, "client@example.com", "s");
        failed.setId(2L);
        failed.setStatus(EmailNotificationStatus.FAILED);
        failed.setAttemptCount(6);
        when(jobRepository.findByEnquiryIdAndNotificationType(9L, EmailNotificationType.ADMIN_ENQUIRY_NOTIFICATION)).thenReturn(Optional.of(sent));
        when(jobRepository.findByEnquiryIdAndNotificationType(9L, EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION)).thenReturn(Optional.of(failed));
        when(jobRepository.findByEnquiryIdOrderByNotificationTypeAsc(9L)).thenReturn(List.of(sent, failed));

        service.retry(9L, "admin");

        assertEquals(EmailNotificationStatus.SENT, sent.getStatus());
        assertEquals(EmailNotificationStatus.PENDING, failed.getStatus());
        assertEquals(0, failed.getAttemptCount());
        verify(worker, never()).process(1L);
        verify(worker).process(2L);
        verify(auditLogService).logAction(eq("admin"), eq("RETRY_EMAIL"), eq("ENQUIRY"), eq("9"), anyString());
    }
}
