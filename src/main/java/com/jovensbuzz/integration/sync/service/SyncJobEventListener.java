package com.jovensbuzz.integration.sync.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts synchronisation in the background once the enquiry and its job are committed,
 * so the worker can always see the rows and a Sheets outage never delays the visitor.
 */
@Component
public class SyncJobEventListener {

    private final SyncJobProcessor syncJobProcessor;

    public SyncJobEventListener(SyncJobProcessor syncJobProcessor) {
        this.syncJobProcessor = syncJobProcessor;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSyncJobQueued(SyncJobQueuedEvent event) {
        syncJobProcessor.processSingleJob(event.syncJobId());
    }
}
