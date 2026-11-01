package com.jovensbuzz.integration.sync.service;

/**
 * Published inside the enquiry transaction; handled only after that transaction commits.
 */
public record SyncJobQueuedEvent(Long syncJobId) {}
