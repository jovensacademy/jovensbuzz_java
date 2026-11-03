package com.jovensbuzz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.integration.email.entity.EmailNotificationStatus;
import com.jovensbuzz.integration.email.entity.EmailNotificationType;
import com.jovensbuzz.integration.email.repository.EmailNotificationJobRepository;
import com.jovensbuzz.integration.sync.entity.SyncStatus;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end flow against the real MySQL/MariaDB test schema (jovens_buzz_test), with Flyway
 * migrations applied. Google Sheets is unconfigured in the test profile, so no external calls
 * are made; the sync job must still be persisted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnquiryFlowIntegrationTest {

    private static final String ADMIN = "test_admin";
    private static final String PASSWORD = "Test-Password#2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EnquiryRepository enquiryRepository;
    @Autowired
    private IntegrationSyncJobRepository syncJobRepository;
    @Autowired
    private EmailNotificationJobRepository emailJobRepository;
    @Autowired
    private com.jovensbuzz.admin.repository.AdminUserRepository adminUserRepository;

    private String token;

    @BeforeEach
    void login() throws Exception {
        // The test owner was bootstrapped with a temporary password; treat it as already changed
        adminUserRepository.findByUsername(ADMIN).ifPresent(o -> { o.setMustChangePassword(false); adminUserRepository.save(o); });
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", ADMIN, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        token = read(result).path("data").path("token").asText();
    }

    @Test
    void flywayCreatedSchema_andSeededContentIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(8)));
        mockMvc.perform(get("/api/v1/faqs")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/site-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hero_headline").exists());
    }

    @Test
    void submittedEnquiry_isStored_queuedForSync_andManageableByAdmin() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        Map<String, Object> payload = Map.of(
                "fullName", "Integration Tester",
                "email", email,
                "phone", "+44 7400 123456",
                "phoneCountry", "GB",
                "companyName", "Flow Ltd",
                "serviceInterest", "Brand Identity",
                "budget", "$1,000 – $2,500",
                "message", "We would like a new brand identity system.",
                "privacyConsent", false,
                "privacyPolicyVersion", "2026-10");

        MvcResult created = mockMvc.perform(post("/api/v1/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        String reference = read(created).path("data").path("publicReference").asText();

        // Stored in MySQL with a durable sync job (Sheets unconfigured, enquiry still safe)
        Enquiry stored = enquiryRepository.findByPublicReference(reference).orElseThrow();
        assertEquals(email, stored.getEmail());
        assertEquals(EnquiryStatus.NEW, stored.getStatus());
        assertFalse(stored.isPrivacyConsent());
        assertEquals("2026-10", stored.getPrivacyPolicyVersion());
        assertFalse(syncJobRepository.findByEnquiryIdOrderByCreatedAtDesc(stored.getId()).isEmpty());
        assertEquals("+447400123456", stored.getPhone(), "phone normalised to E.164");
        assertEquals("GB", stored.getPhoneCountry());

        // Both email jobs were queued in the same transaction; SMTP is unconfigured in tests so they wait as PENDING
        var emailJobs = emailJobRepository.findByEnquiryIdOrderByNotificationTypeAsc(stored.getId());
        assertEquals(2, emailJobs.size());
        emailJobs.forEach(job -> assertEquals(EmailNotificationStatus.PENDING, job.getStatus()));
        assertEquals(email, emailJobs.stream()
                .filter(j -> j.getNotificationType() == EmailNotificationType.CLIENT_ENQUIRY_CONFIRMATION)
                .findFirst().orElseThrow().getRecipientEmail());

        // Identical resubmission is collapsed, not duplicated
        mockMvc.perform(post("/api/v1/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicReference").value(reference))
                .andExpect(jsonPath("$.data.duplicate").value(true));

        // Visible to the admin immediately via search
        mockMvc.perform(get("/api/v1/admin/enquiries").header("Authorization", bearer()).param("search", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value(email));

        // Status workflow with history, notes and audit
        mockMvc.perform(patch("/api/v1/admin/enquiries/" + stored.getId() + "/status")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "CONTACTED", "note", "Intro call booked"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTACTED"))
                .andExpect(jsonPath("$.data.statusHistory.length()").value(2));

        mockMvc.perform(patch("/api/v1/admin/enquiries/" + stored.getId() + "/status")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "NEW"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/enquiries/" + stored.getId() + "/notes").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // Sync status is visible; job waits for configuration rather than being lost
        mockMvc.perform(get("/api/v1/admin/enquiries/" + stored.getId() + "/sync-status").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").exists());

        // Retrying while unconfigured fails safely and keeps a retryable job
        mockMvc.perform(post("/api/v1/admin/enquiries/" + stored.getId() + "/retry-sync").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.synced").value(false));
        assertEquals(SyncStatus.FAILED,
                syncJobRepository.findTopByEnquiryIdAndIntegrationTypeOrderByCreatedAtDesc(stored.getId(), "GOOGLE_SHEETS")
                        .orElseThrow().getStatus());

        // Email notification status is visible to admins; a retry never duplicates jobs
        mockMvc.perform(get("/api/v1/admin/enquiries/" + stored.getId() + "/notifications").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smtpConfigured").value(false))
                .andExpect(jsonPath("$.data.notifications.length()").value(2));
        mockMvc.perform(post("/api/v1/admin/enquiries/" + stored.getId() + "/notifications/retry").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(2))
                .andExpect(jsonPath("$.data.notifications[0].status").value("PENDING"));
        mockMvc.perform(get("/api/v1/enquiries/" + stored.getId() + "/notifications"))
                .andExpect(status().isUnauthorized());

        // CSV export contains the enquiry
        MvcResult csv = mockMvc.perform(get("/api/v1/admin/enquiries/export").header("Authorization", bearer()).param("search", reference))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(csv.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains(reference));
    }

    @Test
    void filtersAndPagination_areAppliedByTheDatabase() throws Exception {
        String marker = "page-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/enquiries")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "fullName", "Pager " + i,
                                    "email", marker + "-" + i + "@example.com",
                                    "phone", "+1 201 555 012" + i,
                                    "companyName", marker,
                                    "serviceInterest", "Content Creation",
                                    "message", "Pagination test enquiry number " + i,
                                    "privacyConsent", true))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/admin/enquiries").header("Authorization", bearer())
                        .param("search", marker).param("size", "2").param("page", "0")
                        .param("service", "Content Creation").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(2));

        mockMvc.perform(get("/api/v1/admin/enquiries").header("Authorization", bearer())
                        .param("search", marker).param("status", "CONVERTED"))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        Instant recent = Instant.now().minus(Duration.ofHours(1));
        assertTrue(enquiryRepository.searchAndFilter(marker, null, null, null, recent, null, PageRequest.of(0, 10))
                .getTotalElements() >= 3);
    }

    @Test
    void dashboardMetrics_comeFromTheDatabase() throws Exception {
        long total = enquiryRepository.count();
        mockMvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEnquiries").value(total));
        mockMvc.perform(get("/api/v1/admin/dashboard/enquiry-trends").header("Authorization", bearer()).param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7));
        mockMvc.perform(get("/api/v1/admin/dashboard/status-distribution").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    void adminApis_requireAuthentication_andLogoutRevokesToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enquiries")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/enquiries/999999999").header("Authorization", bearer()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer())).andExpect(status().isUnauthorized());
    }

    @Test
    void siteContent_rejectsUnknownKeysAndUnsafeUrls() throws Exception {
        mockMvc.perform(put("/api/v1/admin/content/internal_secret").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("contentValue", "x"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/content/social_instagram").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("contentValue", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPassword_isRejectedWithGenericMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "no_such_user", "password", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
