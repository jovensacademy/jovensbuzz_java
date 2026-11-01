package com.jovensbuzz;

import com.jovensbuzz.integration.email.service.EmailSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailSettingsTest {

    private static EmailSettings settings(boolean enabled, boolean auth, String host, String username, String password, String from) {
        return new EmailSettings(enabled, auth, host, 587, username, password, from, "Jovens Buzz",
                "admin@jovensbuzz.com", "", "http://localhost:3000", "", "24 hours");
    }

    @Test
    void fullyConfiguredGmailStyleSmtpIsEnabled() {
        EmailSettings s = settings(true, true, "smtp.gmail.com", "team@jovensbuzz.com", "app-password", "team@jovensbuzz.com");
        assertTrue(s.isSmtpConfigured());
        assertNull(s.disabledReason());
        assertTrue(s.isSmtpPasswordSet());
    }

    @Test
    void smtpEnabledFlagOffDisablesSending() {
        EmailSettings s = settings(false, true, "smtp.gmail.com", "team@jovensbuzz.com", "app-password", "team@jovensbuzz.com");
        assertFalse(s.isSmtpConfigured());
        assertTrue(s.disabledReason().contains("SMTP_ENABLED"));
    }

    @Test
    void missingCredentialsDisableSendingWithAClearReason() {
        assertTrue(settings(true, true, "smtp.gmail.com", "", "app-password", "a@b.com").disabledReason().contains("SMTP_USERNAME"));
        assertTrue(settings(true, true, "smtp.gmail.com", "user", "", "a@b.com").disabledReason().contains("SMTP_PASSWORD"));
        assertTrue(settings(true, true, "", "user", "pw", "a@b.com").disabledReason().contains("SMTP_HOST"));
        assertTrue(settings(true, true, "smtp.gmail.com", "user", "pw", "").disabledReason().contains("SMTP_FROM_EMAIL"));
        // Relays without authentication don't need credentials
        assertTrue(settings(true, false, "relay.internal", "", "", "a@b.com").isSmtpConfigured());
    }

    @Test
    void senderFallsBackToTheSmtpUsername() {
        EmailSettings s = settings(true, true, "smtp.gmail.com", "team@jovensbuzz.com", "app-password", "");
        assertEquals("team@jovensbuzz.com", s.getFromEmail());
        assertTrue(s.isSmtpConfigured());
        // A non-email username (some relays) does not become the sender
        assertFalse(settings(true, true, "relay", "apikey", "pw", "").isSmtpConfigured());
    }

    @Test
    void passwordIsNeverExposed() {
        EmailSettings s = settings(true, true, "smtp.gmail.com", "user", "super-secret", "a@b.com");
        assertFalse(s.toString().contains("super-secret"));
        for (var method : EmailSettings.class.getMethods()) {
            assertNotEquals("getSmtpPassword", method.getName(), "no getter may return the password");
        }
    }

    @Test
    void responseTimeAcceptsBareDurationOrFullPhrase() {
        assertEquals("within 24 hours", settings(true, false, "h", "", "", "a@b.com").getResponseTime());
        EmailSettings phrase = new EmailSettings(true, false, "h", 587, "", "", "a@b.com", "Jovens Buzz", "", "",
                "http://localhost:3000", "", "within one business day");
        assertEquals("within one business day", phrase.getResponseTime());
    }

    @Test
    void logoUrlDefaultsToCidAndSupportsCustomUrl() {
        EmailSettings sDefault = settings(true, true, "smtp.gmail.com", "user", "pass", "a@b.com");
        assertEquals("cid:jovensBuzzLogo", sDefault.getLogoUrl());

        EmailSettings sCustom = new EmailSettings(true, false, "h", 587, "", "", "a@b.com", "Jovens Buzz", "", "",
                "http://localhost:3000", "", "24 hours", "https://cdn.jovensbuzz.com/logo.png");
        assertEquals("https://cdn.jovensbuzz.com/logo.png", sCustom.getLogoUrl());
    }
}
