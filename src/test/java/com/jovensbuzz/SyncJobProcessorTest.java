package com.jovensbuzz;

import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.integration.googlesheets.GoogleSheetsService;
import com.jovensbuzz.integration.googlesheets.SheetRow;
import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;
import com.jovensbuzz.integration.sync.entity.SyncStatus;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import com.jovensbuzz.integration.sync.service.SyncJobProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Google Sheets synchronisation and retry behaviour with the Sheets API mocked out.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SyncJobProcessorTest {

    @Mock
    private IntegrationSyncJobRepository syncJobRepository;
    @Mock
    private GoogleSheetsService googleSheetsService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SyncJobProcessor syncJobProcessor;
    private IntegrationSyncJob syncJob;

    @BeforeEach
    void setUp() {
        syncJobProcessor = new SyncJobProcessor(syncJobRepository, googleSheetsService, transactionManager);

        Enquiry enquiry = new Enquiry();
        enquiry.setId(1L);
        enquiry.setPublicReference("JB-2026-TEST01");
        enquiry.setFullName("Test User");
        enquiry.setEmail("test@user.com");
        enquiry.setServiceInterest("Brand Identity");
        enquiry.setStatus(EnquiryStatus.NEW);
        enquiry.setCreatedAt(Instant.parse("2026-10-30T10:15:30Z"));

        syncJob = new IntegrationSyncJob(enquiry, "GOOGLE_SHEETS");
        syncJob.setId(5L);
        when(syncJobRepository.findById(5L)).thenReturn(Optional.of(syncJob));
        when(syncJobRepository.claim(eq(5L), anyCollection(), eq(SyncStatus.PROCESSING), any()))
                .thenAnswer(inv -> {
                    syncJob.setStatus(SyncStatus.PROCESSING);
                    return 1;
                });
    }

    @Test
    void success_marksJobSucceeded_andStoresSheetRange() throws Exception {
        when(googleSheetsService.isConfigured()).thenReturn(true);
        when(googleSheetsService.appendIfAbsent(any(SheetRow.class))).thenReturn("Enquiries!A2:L2");

        assertTrue(syncJobProcessor.processSingleJob(5L));

        assertEquals(SyncStatus.SUCCESS, syncJob.getStatus());
        assertEquals("Enquiries!A2:L2", syncJob.getExternalRef());
        assertEquals(1, syncJob.getAttemptCount());
        assertNull(syncJob.getLastError());
    }

    @Test
    void sheetRow_containsStoredEnquiryData_inFixedColumnOrder() throws Exception {
        when(googleSheetsService.isConfigured()).thenReturn(true);
        when(googleSheetsService.appendIfAbsent(any(SheetRow.class))).thenAnswer(inv -> {
            SheetRow row = inv.getArgument(0);
            assertEquals("JB-2026-TEST01", row.reference());
            assertEquals(SheetRow.HEADER.size(), row.values().size());
            assertEquals("2026-10-30 10:15:30", row.values().get(1));
            assertEquals("Brand Identity", row.values().get(8));
            return "Enquiries!A3:L3";
        });
        assertTrue(syncJobProcessor.processSingleJob(5L));
    }

    @Test
    void notConfigured_marksFailedWithoutCountingAttempt() throws Exception {
        when(googleSheetsService.isConfigured()).thenReturn(false);

        assertFalse(syncJobProcessor.processSingleJob(5L));

        assertEquals(SyncStatus.FAILED, syncJob.getStatus());
        assertTrue(syncJob.getLastError().contains("not configured"));
        assertEquals(0, syncJob.getAttemptCount());
        verify(googleSheetsService, never()).appendIfAbsent(any());
    }

    @Test
    void apiError_schedulesRetryWithBackoff() throws Exception {
        when(googleSheetsService.isConfigured()).thenReturn(true);
        when(googleSheetsService.appendIfAbsent(any())).thenThrow(new RuntimeException("Network timeout"));

        assertFalse(syncJobProcessor.processSingleJob(5L));

        assertEquals(SyncStatus.RETRYING, syncJob.getStatus());
        assertNotNull(syncJob.getNextRetryAt());
        assertTrue(syncJob.getNextRetryAt().isAfter(Instant.now()));
        assertTrue(syncJob.getLastError().contains("Network timeout"));
    }

    @Test
    void apiError_onFinalAttempt_marksFailed() throws Exception {
        syncJob.setAttemptCount(SyncJobProcessor.MAX_ATTEMPTS - 1);
        when(googleSheetsService.isConfigured()).thenReturn(true);
        when(googleSheetsService.appendIfAbsent(any())).thenThrow(new RuntimeException("503"));

        assertFalse(syncJobProcessor.processSingleJob(5L));

        assertEquals(SyncStatus.FAILED, syncJob.getStatus());
        assertNull(syncJob.getNextRetryAt());
    }

    @Test
    void errorMessages_redactCredentials() throws Exception {
        when(googleSheetsService.isConfigured()).thenReturn(true);
        when(googleSheetsService.appendIfAbsent(any()))
                .thenThrow(new RuntimeException("bad grant access_token=ya29.secretvalue"));

        syncJobProcessor.processSingleJob(5L);

        assertFalse(syncJob.getLastError().contains("ya29.secretvalue"));
    }

    @Test
    void jobAlreadyClaimedOrSynced_isNotProcessedTwice() throws Exception {
        when(syncJobRepository.claim(eq(5L), anyCollection(), eq(SyncStatus.PROCESSING), any())).thenReturn(0);
        syncJob.setStatus(SyncStatus.SUCCESS);

        assertTrue(syncJobProcessor.processSingleJob(5L));
        verify(googleSheetsService, never()).appendIfAbsent(any());
    }
}
