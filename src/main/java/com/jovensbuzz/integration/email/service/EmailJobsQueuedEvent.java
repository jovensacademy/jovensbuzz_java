package com.jovensbuzz.integration.email.service;

import java.util.List;

/**
 * Published inside the enquiry transaction; handled only after that transaction commits.
 */
public record EmailJobsQueuedEvent(List<Long> jobIds) {}
