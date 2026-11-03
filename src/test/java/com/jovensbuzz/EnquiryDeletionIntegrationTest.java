package com.jovensbuzz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovensbuzz.admin.entity.AdminPermission;
import com.jovensbuzz.admin.entity.AdminRole;
import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.repository.AuditLogRepository;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enquiry deletion end to end: soft delete (permission checks, exclusion from lists/analytics/exports,
 * audit trail, no effect on Google Sheets jobs), no restore, and owner-only permanent deletion.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnquiryDeletionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EnquiryRepository enquiryRepository;
    @Autowired private IntegrationSyncJobRepository syncJobRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private final List<String> managers = new ArrayList<>();
    private String ownerToken;
    private Enquiry target;
    private Enquiry other;

    @BeforeEach
    void setUp() throws Exception {
        adminUserRepository.findByUsername("test_admin").ifPresent(o -> { o.setMustChangePassword(false); adminUserRepository.save(o); });
        ownerToken = token("test_admin", "Test-Password#2026");
        target = save("Target " + tag);
        other = save("Other " + tag);
        syncJobRepository.save(new IntegrationSyncJob(target, "GOOGLE_SHEETS"));
    }

    @AfterEach
    void cleanUp() {
        // Test database only (hard delete, including soft-deleted rows)
        jdbc.update("DELETE FROM enquiries WHERE company_name = ? OR email LIKE ?", "DEL-" + tag, "%" + tag + "%");
        managers.forEach(u -> adminUserRepository.findByUsername(u).ifPresent(adminUserRepository::delete));
    }

    @Test
    void ownerDeletes_enquiryIsHiddenEverywhere_butKeptAndAudited() throws Exception {
        long totalBefore = summaryTotal();
        long syncJobsBefore = syncJobsFor(target.getId());

        mockMvc.perform(delete("/api/v1/admin/enquiries/" + target.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Enquiry deleted"));

        // Hidden from detail, list, search, dashboard, Excel and CSV
        mockMvc.perform(get("/api/v1/admin/enquiries/" + target.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/enquiries?search=" + tag).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].publicReference").value(other.getPublicReference()));
        assertEquals(totalBefore - 1, summaryTotal(), "analytics exclude deleted enquiries");
        MvcResult xlsx = mockMvc.perform(get("/api/v1/admin/enquiries/export/excel?search=" + tag).header("Authorization", bearer(ownerToken)))
                .andExpect(header().string("X-Export-Count", "1")).andReturn();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
            assertEquals(8, wb.getSheetAt(0).getLastRowNum(), "header row + 1 data row");
        }
        String csv = mockMvc.perform(get("/api/v1/admin/enquiries/export?search=" + tag).header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(csv.contains(target.getPublicReference()));
        assertTrue(csv.contains(other.getPublicReference()));

        // Kept in the database (Deleted enquiries), audited; Google Sheets job untouched
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM enquiries WHERE id = ? AND deleted_at IS NOT NULL AND deleted_by = 'test_admin'",
                Integer.class, target.getId()));
        assertEquals(syncJobsBefore, syncJobsFor(target.getId()));
        mockMvc.perform(get("/api/v1/admin/enquiries/deleted").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.data[?(@.publicReference == '" + target.getPublicReference() + "')].deletedBy").value("test_admin"));
        assertTrue(auditLogRepository.findAll().stream().anyMatch(a -> "DELETE_ENQUIRY".equals(a.getAction())
                && String.valueOf(target.getId()).equals(a.getEntityId()) && a.getSafeMetadata().contains(target.getPublicReference())));

        // Deleting twice or a missing id: 404, nothing else changes
        mockMvc.perform(delete("/api/v1/admin/enquiries/" + target.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/enquiries/999999999").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        assertTrue(enquiryRepository.findById(other.getId()).isPresent(), "unrelated enquiry untouched");

        // Restore no longer exists, even for owners: the enquiry stays deleted
        mockMvc.perform(post("/api/v1/admin/enquiries/" + target.getId() + "/restore").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM enquiries WHERE id = ? AND deleted_at IS NOT NULL", Integer.class, target.getId()));
        assertEquals(totalBefore - 1, summaryTotal());
    }

    @Test
    void ownerPermanentlyDeletes_onlyFromDeletedEnquiries_auditedFirst_unrelatedUntouched() throws Exception {
        jdbc.update("INSERT INTO enquiry_notes (enquiry_id, admin_user_id, note, created_at) "
                + "SELECT ?, id, 'purge test note', NOW() FROM admin_users WHERE username = 'test_admin'", target.getId());
        syncJobRepository.save(new IntegrationSyncJob(other, "GOOGLE_SHEETS"));
        long otherJobs = syncJobsFor(other.getId());

        // Active enquiries cannot be purged directly
        mockMvc.perform(delete("/api/v1/admin/enquiries/deleted/" + target.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        assertTrue(enquiryRepository.findById(target.getId()).isPresent());

        mockMvc.perform(delete("/api/v1/admin/enquiries/" + target.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        // Unauthenticated and managers (even with ENQUIRIES_DELETE) cannot purge
        mockMvc.perform(delete("/api/v1/admin/enquiries/deleted/" + target.getId())).andExpect(status().isUnauthorized());
        String deleter = manager(EnumSet.of(AdminPermission.ENQUIRIES_VIEW, AdminPermission.ENQUIRIES_DELETE));
        mockMvc.perform(delete("/api/v1/admin/enquiries/deleted/" + target.getId())
                        .header("Authorization", bearer(token(deleter, "Manager#Pass-2026"))))
                .andExpect(status().isForbidden());
        assertEquals(1, rowCount(target.getId()));

        mockMvc.perform(delete("/api/v1/admin/enquiries/deleted/" + target.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Enquiry permanently deleted"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // Gone with its notes, history and job rows; not in Deleted enquiries any more
        assertEquals(0, rowCount(target.getId()));
        assertEquals(0, syncJobsFor(target.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM enquiry_notes WHERE enquiry_id = ?", Integer.class, target.getId()));
        mockMvc.perform(get("/api/v1/admin/enquiries/deleted").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.data[?(@.id == " + target.getId() + ")]").isEmpty());

        // Audited (and the earlier soft-delete entry is kept); no email in the audit text
        String id = String.valueOf(target.getId());
        assertTrue(auditLogRepository.findAll().stream().anyMatch(a -> "PERMANENT_DELETE_ENQUIRY".equals(a.getAction())
                && id.equals(a.getEntityId()) && a.getSafeMetadata().contains(target.getPublicReference())
                && a.getSafeMetadata().contains("by test_admin") && !a.getSafeMetadata().contains(target.getEmail())));
        assertTrue(auditLogRepository.findAll().stream().anyMatch(a -> "DELETE_ENQUIRY".equals(a.getAction()) && id.equals(a.getEntityId())));

        // Unrelated enquiry and its Google Sheets job untouched; a second purge is 404
        assertTrue(enquiryRepository.findById(other.getId()).isPresent());
        assertEquals(otherJobs, syncJobsFor(other.getId()));
        mockMvc.perform(delete("/api/v1/admin/enquiries/deleted/" + target.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/enquiries?search=" + tag).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void managers_needTheExplicitDeletePermission() throws Exception {
        String viewer = manager(EnumSet.of(AdminPermission.ENQUIRIES_VIEW, AdminPermission.ENQUIRIES_MANAGE));
        String token = token(viewer, "Manager#Pass-2026");
        mockMvc.perform(delete("/api/v1/admin/enquiries/" + target.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/enquiries/deleted").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/enquiries/deleted/" + target.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        assertTrue(enquiryRepository.findById(target.getId()).isPresent());
        assertFalse(AdminPermission.MANAGER_DEFAULTS.contains(AdminPermission.ENQUIRIES_DELETE), "never granted by default");

        String deleter = manager(EnumSet.of(AdminPermission.ENQUIRIES_VIEW, AdminPermission.ENQUIRIES_DELETE));
        mockMvc.perform(delete("/api/v1/admin/enquiries/" + target.getId()).header("Authorization", bearer(token(deleter, "Manager#Pass-2026"))))
                .andExpect(status().isOk());
        assertTrue(enquiryRepository.findById(target.getId()).isEmpty());
    }

    @Test
    void deletedEnquiries_doNotSwallowNewSubmissions_andReferencesStayUnique() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("fullName", "Resubmit " + tag, "email", "resubmit-" + tag + "@example.com",
                "phone", "+12015550123", "phoneCountry", "US", "serviceInterest", "Other",
                "message", "Hello, please call me back about a project " + tag));
        MvcResult first = mockMvc.perform(post("/api/v1/enquiries").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String firstRef = objectMapper.readTree(first.getResponse().getContentAsString()).path("data").path("publicReference").asText();
        Long firstId = enquiryRepository.findByPublicReference(firstRef).orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/admin/enquiries/" + firstId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        // The same visitor submits again: a new enquiry, not a "duplicate" of the deleted one
        mockMvc.perform(post("/api/v1/enquiries").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.duplicate").value(false));
        assertTrue(enquiryRepository.existsByPublicReference(firstRef), "deleted references stay reserved");
    }

    // ---------------------------------------------------------------- helpers

    private Enquiry save(String name) {
        Enquiry e = new Enquiry();
        e.setPublicReference("JB-D-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT));
        e.setFullName(name);
        e.setEmail(name.toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com");
        e.setPhone("+12015550123");
        e.setCompanyName("DEL-" + tag);
        e.setServiceInterest("Other");
        e.setMessage("Deletion test " + name);
        e.setStatus(EnquiryStatus.NEW);
        e.setSource("WEBSITE_CONTACT");
        e.setCreatedAt(Instant.now());
        return enquiryRepository.save(e);
    }

    private String manager(Set<AdminPermission> permissions) {
        String username = "it_del_" + UUID.randomUUID().toString().substring(0, 6);
        AdminUser m = new AdminUser(username, passwordEncoder.encode("Manager#Pass-2026"), "Delete Test", username + "@example.com",
                AdminRole.MANAGER.authority());
        m.setAssignedPermissions(permissions);
        adminUserRepository.save(m);
        managers.add(username);
        return username;
    }

    private long summaryTotal() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", bearer(ownerToken))).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("totalEnquiries").asLong();
    }

    private int rowCount(Long enquiryId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM enquiries WHERE id = ?", Integer.class, enquiryId);
    }

    private long syncJobsFor(Long enquiryId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM integration_sync_jobs WHERE enquiry_id = ?", Long.class, enquiryId);
    }

    private String token(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
