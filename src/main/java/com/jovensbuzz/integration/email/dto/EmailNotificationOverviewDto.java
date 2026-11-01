package com.jovensbuzz.integration.email.dto;

import java.util.List;

/**
 * Email notification state for one enquiry, plus whether delivery is currently possible.
 */
public record EmailNotificationOverviewDto(
        boolean smtpConfigured,
        boolean adminRecipientConfigured,
        List<EmailNotificationDto> notifications) {}
