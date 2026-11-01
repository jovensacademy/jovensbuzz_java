package com.jovensbuzz.integration.email.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Email configuration, read from environment variables (see .env.example).
 *
 * Sending is enabled only when SMTP_ENABLED=true and the host, sender address and (when SMTP_AUTH is on)
 * username and password are all present. The password itself is never stored here — only whether one
 * was provided — so it cannot leak through logs or the settings API.
 */
@Component
public class EmailSettings {

    private static final Logger log = LoggerFactory.getLogger(EmailSettings.class);

    private final boolean enabled;
    private final boolean smtpAuth;
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final boolean smtpPasswordSet;
    private final String fromEmail;
    private final String fromName;
    private final String adminNotificationEmail;
    private final String clientSupportEmail;
    private final String siteUrl;
    private final String adminDashboardUrl;
    private final String responseTime;
    private final String logoUrl;

    @Autowired
    public EmailSettings(
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean smtpAuth,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${spring.mail.port:587}") int smtpPort,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${spring.mail.password:}") String smtpPassword,
            @Value("${app.mail.from-email:}") String fromEmail,
            @Value("${app.mail.from-name:Jovens Buzz}") String fromName,
            @Value("${app.mail.admin-notification-email:}") String adminNotificationEmail,
            @Value("${app.mail.client-support-email:}") String clientSupportEmail,
            @Value("${app.urls.site:http://localhost:3000}") String siteUrl,
            @Value("${app.urls.admin-dashboard:}") String adminDashboardUrl,
            @Value("${app.mail.response-time:24 hours}") String responseTime,
            @Value("${app.mail.logo-url:${EMAIL_LOGO_URL:}}") String logoUrl) {
        this.enabled = enabled;
        this.smtpAuth = smtpAuth;
        this.smtpHost = trim(smtpHost);
        this.smtpPort = smtpPort;
        this.smtpUsername = trim(smtpUsername);
        this.smtpPasswordSet = StringUtils.hasText(smtpPassword);
        // Gmail and most providers only allow sending as the authenticated mailbox, so an empty
        // SMTP_FROM_EMAIL falls back to SMTP_USERNAME when that is an email address.
        this.fromEmail = StringUtils.hasText(fromEmail) ? fromEmail.trim()
                : (this.smtpUsername.contains("@") ? this.smtpUsername : "");
        this.fromName = fromName;
        this.adminNotificationEmail = trim(adminNotificationEmail);
        this.clientSupportEmail = trim(clientSupportEmail);
        this.siteUrl = trimSlash(siteUrl);
        this.adminDashboardUrl = StringUtils.hasText(adminDashboardUrl) ? trimSlash(adminDashboardUrl) : this.siteUrl + "/admin";
        this.responseTime = responseTime;
        this.logoUrl = StringUtils.hasText(logoUrl) ? logoUrl.trim() : "cid:jovensBuzzLogo";
    }

    /** 13-argument constructor for backwards compatibility and tests without explicit logoUrl. */
    public EmailSettings(
            boolean enabled,
            boolean smtpAuth,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            String fromEmail,
            String fromName,
            String adminNotificationEmail,
            String clientSupportEmail,
            String siteUrl,
            String adminDashboardUrl,
            String responseTime) {
        this(enabled, smtpAuth, smtpHost, smtpPort, smtpUsername, smtpPassword, fromEmail, fromName,
                adminNotificationEmail, clientSupportEmail, siteUrl, adminDashboardUrl, responseTime, "cid:jovensBuzzLogo");
    }

    /** Convenience constructor (tests): SMTP enabled without authentication. */
    public EmailSettings(String smtpHost, String fromEmail, String fromName, String adminNotificationEmail,
                         String clientSupportEmail, String siteUrl, String adminDashboardUrl, String responseTime) {
        this(true, false, smtpHost, 587, "", "", fromEmail, fromName, adminNotificationEmail, clientSupportEmail,
                siteUrl, adminDashboardUrl, responseTime, "cid:jovensBuzzLogo");
    }

    /** True only when emails can actually be sent. */
    public boolean isSmtpConfigured() {
        return disabledReason() == null;
    }

    /** Why sending is off (safe to show admins — never contains secrets), or null when it is on. */
    public String disabledReason() {
        if (!enabled) return "SMTP is disabled (set SMTP_ENABLED=true)";
        if (!StringUtils.hasText(smtpHost)) return "SMTP is not configured (SMTP_HOST is empty)";
        if (!StringUtils.hasText(fromEmail)) return "SMTP is not configured (set SMTP_FROM_EMAIL)";
        if (smtpAuth && (!StringUtils.hasText(smtpUsername) || !smtpPasswordSet)) {
            return "SMTP credentials are missing (set SMTP_USERNAME and SMTP_PASSWORD)";
        }
        return null;
    }

    public boolean isAdminRecipientConfigured() {
        return StringUtils.hasText(adminNotificationEmail);
    }

    /** Logs the email state at startup without any credentials. */
    @EventListener(ApplicationReadyEvent.class)
    public void logStatus() {
        String reason = disabledReason();
        if (reason == null) {
            log.info("Email notifications enabled via {}:{} as {} (admin recipient {})",
                    smtpHost, smtpPort, fromEmail, isAdminRecipientConfigured() ? "configured" : "NOT configured");
        } else {
            log.info("Email notifications not sending: {}. Enquiries are still saved; emails stay pending.", reason);
        }
    }

    /**
     * Phrase used in the client email, e.g. "within 24 hours". A bare duration such as "24 hours"
     * (the documented CLIENT_RESPONSE_TIME format) is prefixed with "within".
     */
    public String getResponseTime() {
        String value = responseTime == null ? "" : responseTime.trim();
        if (value.isEmpty()) return "within 24 hours";
        return Character.isDigit(value.charAt(0)) ? "within " + value : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimSlash(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSmtpAuth() {
        return smtpAuth;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public boolean isSmtpPasswordSet() {
        return smtpPasswordSet;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public String getAdminNotificationEmail() {
        return adminNotificationEmail;
    }

    public String getClientSupportEmail() {
        return clientSupportEmail;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public String getAdminDashboardUrl() {
        return adminDashboardUrl;
    }

    public String getLogoUrl() {
        return logoUrl;
    }
}
