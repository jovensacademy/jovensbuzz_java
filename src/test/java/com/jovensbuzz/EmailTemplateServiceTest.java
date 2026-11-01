package com.jovensbuzz;

import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.integration.email.service.EmailSettings;
import com.jovensbuzz.integration.email.service.EmailTemplateService;
import com.jovensbuzz.integration.email.service.EmailTemplateService.EmailContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateServiceTest {

    private EmailTemplateService templates;
    private Enquiry enquiry;

    @BeforeEach
    void setUp() {
        EmailSettings settings = new EmailSettings("smtp.example.com", "hello@jovensbuzz.com", "Jovens Buzz",
                "admin@jovensbuzz.com", "", "https://jovensbuzz.com/", "", "within 24 hours");
        templates = new EmailTemplateService(settings, "America/Chicago");

        enquiry = new Enquiry();
        enquiry.setId(42L);
        enquiry.setPublicReference("JB-2026-7KQ4MX");
        enquiry.setFullName("Ada Lovelace");
        enquiry.setEmail("ada@example.com");
        enquiry.setPhone("+12015550123");
        enquiry.setPhoneCountry("US");
        enquiry.setCompanyName("Analytical Engines");
        enquiry.setServiceInterest("Brand Identity");
        enquiry.setMessage("Line one <script>alert('x')</script>\nLine two");
        enquiry.setCreatedAt(Instant.parse("2026-10-30T15:30:00Z"));
    }

    @Test
    void adminNotificationContainsAllEnquiryDetailsAndDashboardLinkWithoutReferenceId() {
        EmailContent email = templates.adminNotification(enquiry);

        assertEquals("New Enquiry Received - Jovens Buzz", email.subject());
        for (String expected : new String[]{"Hello Admin,", "Ada Lovelace", "ada@example.com",
                "+1 201-555-0123 (US)", "Analytical Engines", "Brand Identity", "Not provided",
                "Oct 30, 2026 at 10:30 AM CDT", "View Enquiry in Admin Dashboard"}) {
            assertTrue(email.html().contains(expected), "html missing: " + expected);
            assertTrue(email.text().contains(expected), "text missing: " + expected);
        }
        // Reference ID must not appear in admin email
        assertFalse(email.html().contains("JB-2026-7KQ4MX"));
        assertFalse(email.text().contains("JB-2026-7KQ4MX"));
        assertFalse(email.html().contains("Enquiry Reference"));
        assertFalse(email.text().contains("Enquiry Reference"));

        // Header logo
        assertTrue(email.html().contains("alt=\"Jovens Buzz\""));
        assertTrue(email.html().contains("width=\"146\""));
        assertTrue(email.html().contains("height=\"55\""));
        assertTrue(email.html().contains("cid:jovensBuzzLogo"));

        assertTrue(email.html().contains("href=\"https://jovensbuzz.com/admin/enquiries/42\""));
    }

    @Test
    void userInputIsEscapedNeverRenderedAsHtml() {
        String html = templates.adminNotification(enquiry).html();
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("Line one"), "message kept");
        assertTrue(html.contains("<br>Line two"), "line breaks preserved");

        enquiry.setFullName("<img src=x onerror=alert(1)>");
        String client = templates.clientConfirmation(enquiry).html();
        assertFalse(client.contains("<img src=x"));
    }

    @Test
    void clientConfirmationHasNameServiceAndResponseCommitmentWithoutReferenceId() {
        EmailContent email = templates.clientConfirmation(enquiry);

        assertEquals("Thank You for Contacting Jovens Buzz!", email.subject());
        for (String expected : new String[]{"Hello Ada Lovelace,", "Thank you for reaching out to Jovens Buzz!",
                "get in touch with you within 24 hours", "Brand Identity", "Best Regards,", "Jovens Buzz Team", "jovensbuzz.com"}) {
            assertTrue(email.text().contains(expected), "text missing: " + expected);
            assertTrue(email.html().contains(expected), "html missing: " + expected);
        }
        // Reference ID must not appear in client email
        assertFalse(email.html().contains("JB-2026-7KQ4MX"));
        assertFalse(email.text().contains("JB-2026-7KQ4MX"));
        assertFalse(email.html().contains("Your Enquiry Reference"));
        assertFalse(email.text().contains("Your Enquiry Reference"));

        // Header logo
        assertTrue(email.html().contains("alt=\"Jovens Buzz\""));
        assertTrue(email.html().contains("width=\"146\""));
        assertTrue(email.html().contains("height=\"55\""));
        assertTrue(email.html().contains("cid:jovensBuzzLogo"));

        // Client email never exposes admin information
        assertFalse(email.html().contains("/admin"));
        assertFalse(email.html().contains("admin@jovensbuzz.com"));
    }

    @Test
    void responseCommitmentIsConfigurable() {
        EmailSettings settings = new EmailSettings("h", "f@x.com", "Jovens Buzz", "", "", "https://jovensbuzz.com", "",
                "within one business day");
        EmailContent email = new EmailTemplateService(settings, "UTC").clientConfirmation(enquiry);
        assertTrue(email.text().contains("get in touch with you within one business day"));
    }
}
