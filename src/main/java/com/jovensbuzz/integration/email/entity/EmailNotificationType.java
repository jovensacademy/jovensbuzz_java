package com.jovensbuzz.integration.email.entity;

public enum EmailNotificationType {
    /** "New Enquiry Received" email to the configured administrator address. */
    ADMIN_ENQUIRY_NOTIFICATION,
    /** "Thank You for Contacting Jovens Buzz!" email to the visitor. */
    CLIENT_ENQUIRY_CONFIRMATION
}
