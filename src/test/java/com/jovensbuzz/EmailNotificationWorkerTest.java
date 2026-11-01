package com.jovensbuzz;

import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.integration.email.entity.EmailNotificationJob;
import com.jovensbuzz.integration.email.entity.EmailNotificationStatus;
import com.jovensbuzz.integration.email.entity.EmailNotificationType;
import com.jovensbuzz.integration.email.repository.EmailNotificationJobRepository;
import com.jovensbuzz.integration.email.service.EmailNotificationWorker;
import com.jovensbuzz.integration.email.service.EmailSettings;
import com.jovensbuzz.integration.email.service.EmailTemplateService;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Email delivery with a mocked SMTP sender — no real email is ever sent in tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailNotificationWorkerTest {

    @Mock
    private EmailNotificationJobRepository jobRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    private PlatformTransactionManager transactionManager;

    private EmailNotificationJob clientJob;
    private EmailNotificationJob adminJob;

    @BeforeEach
    void setUp() {
        Enquiry enquiry = new Enquiry();
        enquiry.setId(7L);
        enquiry.setPublicReference("JB-2026-ABCDEF");
        enquiry.setFullName("Grace Hopper");
        enquiry.setEmail("grace@example.com");
        enquiry.setPhone("+919876543210");
        enquiry.setPhoneCountry("IN");
        enquiry.setServiceInterest("Content Creation");
        enquiry.setMessage("Need monthly content.");
        enquiry.setCreatedAt(Instant.now());

        clientJob = new EmailNotificationJob(enquiry, EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION, "grace@example.com",
                EmailTemplateService.CLIENT_SUBJECT);
        clientJob.setId(1L);
        adminJob = new EmailNotificationJob(enquiry, EmailNotificationType.ADMIN_ENQUIRY_NOTIFICATION, null,
                EmailTemplateService.ADMIN_SUBJECT);
        adminJob.setId(2L);

        when(jobRepository.findById(1L)).thenReturn(Optional.of(clientJob));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(adminJob));
        when(jobRepository.claim(anyLong(), eq(EmailNotificationStatus.PENDING), eq(EmailNotificationStatus.PROCESSING), any()))
                .thenAnswer(inv -> {
                    EmailNotificationJob job = inv.getArgument(0, Long.class) == 1L ? clientJob : adminJob;
                    if (job.getStatus() != EmailNotificationStatus.PENDING) return 0;
                    job.setStatus(EmailNotificationStatus.PROCESSING);
                    return 1;
                });
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
    }

    private EmailNotificationWorker worker(boolean smtpConfigured, String adminEmail) {
        EmailSettings settings = new EmailSettings(smtpConfigured ? "smtp.example.com" : "", "hello@jovensbuzz.com",
                "Jovens Buzz", adminEmail, "support@jovensbuzz.com", "https://jovensbuzz.com", "", "within 24 hours");
        return new EmailNotificationWorker(jobRepository, new EmailTemplateService(settings, "UTC"), settings,
                mailSenderProvider, transactionManager);
    }

    @Test
    void sendsClientConfirmation_andMarksSent() throws Exception {
        assertTrue(worker(true, "admin@jovensbuzz.com").process(1L));

        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(message.capture());
        MimeMessage sent = message.getValue();
        assertEquals("Thank You for Contacting Jovens Buzz!", sent.getSubject());
        assertEquals("grace@example.com", sent.getRecipients(Message.RecipientType.TO)[0].toString());
        Address from = sent.getFrom()[0];
        assertTrue(from.toString().contains("hello@jovensbuzz.com") && from.toString().contains("Jovens Buzz"));
        assertEquals("support@jovensbuzz.com", sent.getReplyTo()[0].toString());

        assertEquals(EmailNotificationStatus.SENT, clientJob.getStatus());
        assertEquals(1, clientJob.getAttemptCount());
        assertNotNull(clientJob.getSentAt());
    }

    @Test
    void adminNotificationGoesToConfiguredAdminAddress() throws Exception {
        assertTrue(worker(true, "admin@jovensbuzz.com").process(2L));
        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(message.capture());
        assertEquals("admin@jovensbuzz.com", message.getValue().getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("New Enquiry Received - Jovens Buzz", message.getValue().getSubject());
    }

    @Test
    void smtpFailure_schedulesRetryWithBackoff_thenFailsAfterMaxAttempts() {
        doThrow(new MailSendException("421 Service not available")).when(mailSender).send(any(MimeMessage.class));
        EmailNotificationWorker worker = worker(true, "admin@jovensbuzz.com");

        assertFalse(worker.process(1L));
        assertEquals(EmailNotificationStatus.PENDING, clientJob.getStatus());
        assertEquals(1, clientJob.getAttemptCount());
        assertNotNull(clientJob.getNextRetryAt());
        assertTrue(clientJob.getLastError().contains("421"));

        clientJob.setAttemptCount(EmailNotificationWorker.MAX_ATTEMPTS - 1);
        clientJob.setNextRetryAt(null);
        assertFalse(worker.process(1L));
        assertEquals(EmailNotificationStatus.FAILED, clientJob.getStatus());
        assertNull(clientJob.getNextRetryAt());
    }

    @Test
    void sentEmailIsNeverSentAgain() {
        EmailNotificationWorker worker = worker(true, "admin@jovensbuzz.com");
        assertTrue(worker.process(1L));
        assertTrue(worker.process(1L)); // already SENT -> claim fails -> reported as sent, no second send
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void unconfiguredSmtp_leavesJobPendingWithoutConsumingAttempts() {
        assertFalse(worker(false, "admin@jovensbuzz.com").process(1L));
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertEquals(EmailNotificationStatus.PENDING, clientJob.getStatus());
        assertEquals(0, clientJob.getAttemptCount());
        assertTrue(clientJob.getLastError().contains("SMTP_HOST"), "explains why sending is off");
    }

    @Test
    void missingAdminRecipient_leavesAdminJobPending() {
        assertFalse(worker(true, "").process(2L));
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertEquals(EmailNotificationStatus.PENDING, adminJob.getStatus());
        assertEquals(EmailNotificationWorker.ADMIN_RECIPIENT_NOT_CONFIGURED, adminJob.getLastError());
    }
}
