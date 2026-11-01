package com.jovensbuzz;

import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.enquiry.dto.EnquiryCreateRequest;
import com.jovensbuzz.enquiry.dto.EnquiryStatusUpdateRequest;
import com.jovensbuzz.enquiry.dto.EnquirySubmissionResponse;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.enquiry.repository.EnquiryNoteRepository;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.enquiry.repository.EnquiryStatusHistoryRepository;
import com.jovensbuzz.enquiry.service.EnquiryService;
import com.jovensbuzz.integration.email.service.EmailNotificationService;
import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import com.jovensbuzz.integration.sync.service.SyncJobProcessor;
import com.jovensbuzz.integration.sync.service.SyncJobQueuedEvent;
import com.jovensbuzz.servicecatalog.entity.ServiceItem;
import com.jovensbuzz.servicecatalog.repository.ServiceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EnquiryServiceTest {

    @Mock
    private EnquiryRepository enquiryRepository;
    @Mock
    private EnquiryNoteRepository enquiryNoteRepository;
    @Mock
    private EnquiryStatusHistoryRepository statusHistoryRepository;
    @Mock
    private IntegrationSyncJobRepository syncJobRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private ServiceItemRepository serviceItemRepository;
    @Mock
    private SyncJobProcessor syncJobProcessor;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private EnquiryService enquiryService;

    private Enquiry testEnquiry;

    @BeforeEach
    void setUp() {
        testEnquiry = new Enquiry();
        testEnquiry.setId(1L);
        testEnquiry.setPublicReference("JB-2026-TEST01");
        testEnquiry.setFullName("Alex Morgan");
        testEnquiry.setEmail("alex@example.com");
        testEnquiry.setServiceInterest("Social Media Strategy");
        testEnquiry.setStatus(EnquiryStatus.NEW);

        ServiceItem strategy = new ServiceItem();
        strategy.setTitle("Social Media Strategy");
        when(serviceItemRepository.findByPublishedTrueOrderByDisplayOrderAsc()).thenReturn(List.of(strategy));
        when(enquiryRepository.findById(1L)).thenReturn(Optional.of(testEnquiry));
        when(enquiryRepository.existsById(1L)).thenReturn(true);
        when(enquiryRepository.findFirstBySubmissionFingerprintAndCreatedAtAfterOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());
    }

    private EnquiryCreateRequest validRequest() {
        EnquiryCreateRequest request = new EnquiryCreateRequest();
        request.setFullName("Alex Morgan");
        request.setEmail("Alex@Example.com");
        request.setPhone("(201) 555-0123");
        request.setPhoneCountry("US");
        request.setCompanyName("Morgan Tech");
        request.setServiceInterest("social media strategy");
        request.setMessage("Looking for full social media management.");
        request.setPrivacyConsent(true);
        return request;
    }

    @Test
    void createEnquiry_persistsEnquiryAndJob_andQueuesSyncAfterCommit() {
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> {
            Enquiry e = invocation.getArgument(0);
            e.setId(1L);
            return e;
        });
        IntegrationSyncJob job = new IntegrationSyncJob(testEnquiry, "GOOGLE_SHEETS");
        job.setId(10L);
        when(syncJobRepository.save(any(IntegrationSyncJob.class))).thenReturn(job);

        EnquirySubmissionResponse response = enquiryService.createEnquiry(validRequest());

        assertNotNull(response);
        assertFalse(response.isDuplicate());
        assertTrue(response.getPublicReference().matches("^JB-\\d{4}-[2-9A-HJ-NP-Z]{6}$"));

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(saved.capture());
        assertEquals("alex@example.com", saved.getValue().getEmail(), "email is normalised");
        assertEquals("Social Media Strategy", saved.getValue().getServiceInterest(), "service resolved to canonical title");
        assertNotNull(saved.getValue().getSubmissionFingerprint());
        verify(syncJobRepository, times(1)).save(any(IntegrationSyncJob.class));
        verify(eventPublisher).publishEvent(new SyncJobQueuedEvent(10L));
        verifyNoInteractions(syncJobProcessor);
        verify(emailNotificationService).queueForNewEnquiry(saved.getValue());
        assertEquals("+12015550123", saved.getValue().getPhone(), "phone stored in E.164");
        assertEquals("US", saved.getValue().getPhoneCountry());
        assertEquals("2026-10", saved.getValue().getPrivacyPolicyVersion());
    }

    @Test
    void createEnquiry_withoutPrivacyConsent_recordsFalseAndPolicyVersion() {
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        IntegrationSyncJob job = new IntegrationSyncJob(testEnquiry, "GOOGLE_SHEETS");
        job.setId(12L);
        when(syncJobRepository.save(any(IntegrationSyncJob.class))).thenReturn(job);

        EnquiryCreateRequest request = validRequest();
        request.setPrivacyConsent(false);
        request.setPrivacyPolicyVersion("2026-10");
        enquiryService.createEnquiry(request);

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository, atLeastOnce()).save(saved.capture());
        assertFalse(saved.getValue().isPrivacyConsent());
        assertEquals("2026-10", saved.getValue().getPrivacyPolicyVersion());
    }

    @Test
    void createEnquiry_stripsMarkupFromFreeText() {
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        IntegrationSyncJob job = new IntegrationSyncJob(testEnquiry, "GOOGLE_SHEETS");
        job.setId(11L);
        when(syncJobRepository.save(any(IntegrationSyncJob.class))).thenReturn(job);

        EnquiryCreateRequest request = validRequest();
        request.setFullName("<b>Alex</b> Morgan");
        request.setMessage("Hello <script>alert(1)</script> we need a new website please");
        enquiryService.createEnquiry(request);

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(saved.capture());
        assertEquals("Alex Morgan", saved.getValue().getFullName());
        assertFalse(saved.getValue().getMessage().contains("<"));
    }

    @Test
    void createEnquiry_duplicateWithinWindow_returnsOriginalReference() {
        testEnquiry.setCreatedAt(Instant.now());
        when(enquiryRepository.findFirstBySubmissionFingerprintAndCreatedAtAfterOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(Optional.of(testEnquiry));

        EnquirySubmissionResponse response = enquiryService.createEnquiry(validRequest());

        assertTrue(response.isDuplicate());
        assertEquals("JB-2026-TEST01", response.getPublicReference());
        verify(enquiryRepository, never()).save(any());
        verify(syncJobRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(emailNotificationService);
    }

    @Test
    void createEnquiry_honeypotFilled_isRejected() {
        EnquiryCreateRequest request = validRequest();
        request.setWebsite("http://spam.example");
        assertThrows(BadRequestException.class, () -> enquiryService.createEnquiry(request));
        verify(enquiryRepository, never()).save(any());
    }

    @Test
    void createEnquiry_unknownService_isRejected() {
        EnquiryCreateRequest request = validRequest();
        request.setServiceInterest("Crypto Pump Campaign");
        assertThrows(BadRequestException.class, () -> enquiryService.createEnquiry(request));
    }

    @Test
    void createEnquiry_genericServiceOption_isAccepted() {
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        IntegrationSyncJob job = new IntegrationSyncJob(testEnquiry, "GOOGLE_SHEETS");
        job.setId(12L);
        when(syncJobRepository.save(any(IntegrationSyncJob.class))).thenReturn(job);

        EnquiryCreateRequest request = validRequest();
        request.setServiceInterest("not sure yet");
        enquiryService.createEnquiry(request);

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(saved.capture());
        assertEquals("Not sure yet", saved.getValue().getServiceInterest());
    }

    @Test
    void createEnquiry_untrustedSource_fallsBackToDefault() {
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        IntegrationSyncJob job = new IntegrationSyncJob(testEnquiry, "GOOGLE_SHEETS");
        job.setId(13L);
        when(syncJobRepository.save(any(IntegrationSyncJob.class))).thenReturn(job);

        EnquiryCreateRequest request = validRequest();
        request.setSource("ADMIN_IMPORT'; DROP TABLE");
        enquiryService.createEnquiry(request);

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(saved.capture());
        assertEquals("WEBSITE_LANDING", saved.getValue().getSource());
    }

    @Test
    void createEnquiry_resolvesPublishedServiceById() {
        ServiceItem video = new ServiceItem();
        video.setTitle("Short-Form Video Production");
        video.setPublished(true);
        when(serviceItemRepository.findById(4L)).thenReturn(Optional.of(video));
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        IntegrationSyncJob job = new IntegrationSyncJob(testEnquiry, "GOOGLE_SHEETS");
        job.setId(14L);
        when(syncJobRepository.save(any(IntegrationSyncJob.class))).thenReturn(job);

        EnquiryCreateRequest request = validRequest();
        request.setServiceInterest(null);
        request.setServiceId(4L);
        enquiryService.createEnquiry(request);

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(saved.capture());
        assertEquals("Short-Form Video Production", saved.getValue().getServiceInterest());
    }

    @Test
    void createEnquiry_unpublishedOrUnknownServiceId_isRejected() {
        ServiceItem hidden = new ServiceItem();
        hidden.setTitle("Hidden");
        hidden.setPublished(false);
        when(serviceItemRepository.findById(5L)).thenReturn(Optional.of(hidden));
        EnquiryCreateRequest request = validRequest();
        request.setServiceId(5L);
        assertThrows(BadRequestException.class, () -> enquiryService.createEnquiry(request));
        request.setServiceId(999L);
        assertThrows(BadRequestException.class, () -> enquiryService.createEnquiry(request));
        verify(enquiryRepository, never()).save(any());
    }

    @Test
    void createEnquiry_invalidPhoneForSelectedCountry_isRejected() {
        EnquiryCreateRequest request = validRequest();
        request.setPhone("+12015550123");
        request.setPhoneCountry("CA"); // a valid US number is not a valid Canadian number
        assertThrows(BadRequestException.class, () -> enquiryService.createEnquiry(request));
        verify(enquiryRepository, never()).save(any());
        verifyNoInteractions(emailNotificationService);
    }

    @Test
    void updateStatus_validTransition_recordsHistoryNoteAndAudit() {
        when(enquiryRepository.save(any(Enquiry.class))).thenReturn(testEnquiry);

        EnquiryStatusUpdateRequest updateReq = new EnquiryStatusUpdateRequest("QUALIFIED", "High budget prospective client");
        enquiryService.updateStatus(1L, updateReq, "jovens_buzz_2026");

        assertEquals(EnquiryStatus.QUALIFIED, testEnquiry.getStatus());
        verify(statusHistoryRepository, times(1)).save(any());
        verify(enquiryNoteRepository, times(1)).save(any());
        verify(auditLogService, times(1)).logAction(eq("jovens_buzz_2026"), eq("UPDATE_STATUS"), eq("ENQUIRY"), eq("1"), anyString());
    }

    @Test
    void updateStatus_invalidStatus_throws() {
        EnquiryStatusUpdateRequest updateReq = new EnquiryStatusUpdateRequest("INVALID_STATUS", "Invalid");
        assertThrows(BadRequestException.class, () -> enquiryService.updateStatus(1L, updateReq, "jovens_buzz_2026"));
    }

    @Test
    void updateStatus_disallowedTransition_throws() {
        testEnquiry.setStatus(EnquiryStatus.CONVERTED);
        EnquiryStatusUpdateRequest updateReq = new EnquiryStatusUpdateRequest("NEW", null);
        assertThrows(BadRequestException.class, () -> enquiryService.updateStatus(1L, updateReq, "admin"));
        verify(statusHistoryRepository, never()).save(any());
    }

    @Test
    void updateStatus_sameStatus_throws() {
        EnquiryStatusUpdateRequest updateReq = new EnquiryStatusUpdateRequest("NEW", null);
        assertThrows(BadRequestException.class, () -> enquiryService.updateStatus(1L, updateReq, "admin"));
    }

    @Test
    void safeSort_ignoresUnknownFields() {
        assertEquals("createdAt", EnquiryService.safeSort("password_hash", "asc").iterator().next().getProperty());
        assertEquals("fullName", EnquiryService.safeSort("fullName", "asc").iterator().next().getProperty());
    }

    @Test
    void enquiryStatus_transitionRules() {
        assertTrue(EnquiryStatus.NEW.canTransitionTo(EnquiryStatus.CONTACTED));
        assertTrue(EnquiryStatus.SPAM.canTransitionTo(EnquiryStatus.NEW));
        assertFalse(EnquiryStatus.CONVERTED.canTransitionTo(EnquiryStatus.NEW));
        assertFalse(EnquiryStatus.NEW.canTransitionTo(EnquiryStatus.CONVERTED));
    }
}
