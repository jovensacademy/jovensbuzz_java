package com.jovensbuzz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.admin.service.AdminBootstrapService;
import com.jovensbuzz.admin.service.AdminUserManagementService;
import com.jovensbuzz.audit.repository.AuditLogRepository;
import com.jovensbuzz.common.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Owner/Manager RBAC, user management safety rules and database-backed authentication, end to end
 * through the real security filter chain and JWTs (jovens_buzz_test database).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserManagementIntegrationTest {

    private static final String OWNER = "test_admin";
    private static final String OWNER_PASSWORD = "Test-Password#2026";
    private static final String INITIAL_PASSWORD = "Initial#Pass-2026x";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AdminUserManagementService managementService;
    @Autowired private AdminBootstrapService bootstrapService;
    @Autowired private PasswordEncoder passwordEncoder;

    private final List<String> created = new ArrayList<>();
    private String ownerToken;

    @BeforeEach
    void signInOwner() throws Exception {
        // The test owner was bootstrapped with a temporary password; treat it as already changed
        adminUserRepository.findByUsername(OWNER).ifPresent(o -> { o.setMustChangePassword(false); adminUserRepository.save(o); });
        ownerToken = login(OWNER, OWNER_PASSWORD).path("data").path("token").asText();
        assertFalse(ownerToken.isBlank());
    }

    @AfterEach
    void removeTestUsers() {
        // Test database only: remove the accounts this test created so runs stay independent
        created.forEach(u -> adminUserRepository.findByUsername(u).ifPresent(adminUserRepository::delete));
    }

    @Test
    void ownerProfile_showsOwnerRoleAllPermissionsAndTimezone() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminRole").value("OWNER"))
                .andExpect(jsonPath("$.data.permissions", org.hamcrest.Matchers.hasItem("USERS_MANAGE")))
                .andExpect(jsonPath("$.data.timezone").isNotEmpty());
    }

    @Test
    void manager_getsDefaultPermissions_andApiEnforcesThem() throws Exception {
        String manager = createUser("MANAGER", null, "Asia/Kolkata");
        // The owner-set initial password works straight away: no forced change, no restricted session
        JsonNode session = login(manager, INITIAL_PASSWORD).path("data");
        assertTrue(session.path("mustChangePassword").isMissingNode(), "no password-change flag in the login response");
        String token = session.path("token").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.adminRole").value("MANAGER"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.data.permissions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("USERS_MANAGE"))));
        expect(token, "GET", "/api/v1/admin/enquiries", 200);
        expect(token, "GET", "/api/v1/admin/users", 200);
        expect(token, "GET", "/api/v1/admin/dashboard/summary", 200);
        expect(token, "GET", "/api/v1/admin/admin-users", 403);
        expect(token, "GET", "/api/v1/admin/content", 403);
        expect(token, "GET", "/api/v1/admin/settings/integrations/google-sheets", 403);
        expect(token, "GET", "/api/v1/admin/audit-logs", 403);
        // A manager can never create, change or delete accounts (including the owner's)
        mockMvc.perform(post("/api/v1/admin/admin-users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        Long ownerId = adminUserRepository.findByUsername(OWNER).orElseThrow().getId();
        expect(token, "DELETE", "/api/v1/admin/admin-users/" + ownerId, 403);
    }

    @Test
    void ownerChangingPermissions_appliesToExistingSessionsImmediately() throws Exception {
        String manager = createUser("MANAGER", null, "Asia/Kolkata");
        String token = activate(manager);
        Long id = idOf(manager);

        update(id, "MANAGER", List.of("ENQUIRIES_VIEW"), 200);
        expect(token, "GET", "/api/v1/admin/enquiries", 200);
        mockMvc.perform(patch("/api/v1/admin/enquiries/999999/status").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isForbidden());
        expect(token, "GET", "/api/v1/admin/enquiries/export/excel", 403);
        expect(token, "GET", "/api/v1/admin/enquiries/import/template", 403);

        update(id, "MANAGER", List.of("ENQUIRIES_VIEW", "CONTENT_MANAGE", "ENQUIRIES_EXPORT"), 200);
        expect(token, "GET", "/api/v1/admin/content", 200);
        expect(token, "GET", "/api/v1/admin/enquiries/export/excel", 200);
    }

    @Test
    void ownerOnlyPermission_andFixedOffsetTimezones_areRejected() throws Exception {
        String manager = createUser("MANAGER", null, "America/Chicago");
        update(idOf(manager), "MANAGER", List.of("USERS_MANAGE"), 400);
        for (String zone : List.of("+05:30", "GMT+5", "UTC-06:00", "Etc/GMT+6", "Mars/Olympus")) {
            mockMvc.perform(post("/api/v1/admin/admin-users").header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(userBody("it_tz_" + suffix(), "MANAGER", null, zone))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void disablingRevokesSessions_andBlocksSignIn_untilEnabled() throws Exception {
        String manager = createUser("MANAGER", null, "Asia/Kolkata");
        String token = activate(manager);
        Long id = idOf(manager);

        setEnabled(id, false, 200);
        expect(token, "GET", "/api/v1/admin/enquiries", 401);
        loginExpect(manager, INITIAL_PASSWORD, 401);

        setEnabled(id, true, 200);
        loginExpect(manager, INITIAL_PASSWORD, 200);
    }

    @Test
    void delete_isSoft_revokesSessions_blocksSignIn_andIsAudited() throws Exception {
        String manager = createUser("MANAGER", null, "Asia/Kolkata");
        String token = activate(manager);
        Long id = idOf(manager);

        mockMvc.perform(delete("/api/v1/admin/admin-users/" + id).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        expect(token, "GET", "/api/v1/admin/enquiries", 401);
        loginExpect(manager, INITIAL_PASSWORD, 401);

        AdminUser row = adminUserRepository.findById(id).orElseThrow();
        assertNotNull(row.getDeletedAt(), "row kept for history");
        assertEquals(OWNER, row.getDeletedBy());
        assertFalse(row.isEnabled());
        assertTrue(auditLogRepository.findAll().stream().anyMatch(a ->
                "DELETE_ADMIN_USER".equals(a.getAction()) && String.valueOf(id).equals(a.getEntityId())));

        String list = mockMvc.perform(get("/api/v1/admin/admin-users").header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(list.contains("\"" + manager + "\""), "deleted users are not listed");
        String assignees = mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(assignees.contains("\"" + manager + "\""), "deleted users cannot be assigned");
        expect(ownerToken, "DELETE", "/api/v1/admin/admin-users/" + id, 404);
    }

    @Test
    void ownerCannotDisableDeleteOrDemoteThemselves() throws Exception {
        Long ownerId = idOf(OWNER);
        mockMvc.perform(delete("/api/v1/admin/admin-users/" + ownerId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        setEnabled(ownerId, false, 400);
        update(ownerId, "MANAGER", List.of("ENQUIRIES_VIEW"), 400);
        assertTrue(adminUserRepository.findById(ownerId).orElseThrow().isActive());
    }

    @Test
    void lastActiveOwner_cannotBeDeletedDisabledOrDemoted_evenByAnotherActor() {
        Long ownerId = idOf(OWNER);
        // Defence in depth: called directly with a different actor name (e.g. a racing request)
        assertThrows(BadRequestException.class, () -> managementService.delete(ownerId, "someone_else"));
        assertThrows(BadRequestException.class, () -> managementService.setEnabled(ownerId, false, "someone_else"));
        assertThrows(BadRequestException.class, () -> managementService.update(ownerId,
                new com.jovensbuzz.admin.dto.AdminUserRequests.Update("Owner", "owner@example.com", "MANAGER", null, "America/Chicago"),
                "someone_else"));
        assertTrue(adminUserRepository.findById(ownerId).orElseThrow().isActive());
    }

    @Test
    void secondOwner_canBeRemoved_whileAnotherOwnerRemains() throws Exception {
        String second = createUser("OWNER", null, "America/Chicago");
        String token = activate(second);
        expect(token, "GET", "/api/v1/admin/admin-users", 200); // owners hold every permission
        mockMvc.perform(delete("/api/v1/admin/admin-users/" + idOf(second)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void ownerSetsNewPassword_revokesSessions_isAudited_andWorksImmediately() throws Exception {
        String manager = createUser("MANAGER", null, "Asia/Kolkata");
        String token = activate(manager);
        mockMvc.perform(post("/api/v1/admin/admin-users/" + idOf(manager) + "/reset-password").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", "Reset#Pass-2026y"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChangedAt").isNotEmpty());
        expect(token, "GET", "/api/v1/admin/enquiries", 401);      // existing sessions revoked
        loginExpect(manager, INITIAL_PASSWORD, 401);               // old password no longer works
        String fresh = login(manager, "Reset#Pass-2026y").path("data").path("token").asText();
        expect(fresh, "GET", "/api/v1/admin/enquiries", 200);      // full access at once, nothing to change
        AdminUser row = adminUserRepository.findByUsername(manager).orElseThrow();
        assertTrue(row.getPasswordHash().startsWith("$2"), "stored as a BCrypt hash");
        assertFalse(row.getPasswordHash().contains("Reset#Pass-2026y"));
        assertTrue(auditLogRepository.findAll().stream().anyMatch(a ->
                "RESET_ADMIN_PASSWORD".equals(a.getAction()) && String.valueOf(row.getId()).equals(a.getEntityId())));
        // Older clients that still send "temporaryPassword" keep working
        mockMvc.perform(post("/api/v1/admin/admin-users/" + idOf(manager) + "/reset-password").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("temporaryPassword", "Alias#Pass-2026q"))))
                .andExpect(status().isOk());
        loginExpect(manager, "Alias#Pass-2026q", 200);
    }


    @Test
    void managers_getForbiddenOnEveryUserManagementApi_andSelfServiceChangeIsGone() throws Exception {
        String manager = createUser("MANAGER", List.of("ENQUIRIES_VIEW", "ENQUIRIES_MANAGE"), "Asia/Kolkata");
        String other = createUser("MANAGER", null, "Asia/Kolkata");
        String token = activate(manager);
        Long ownerId = idOf(OWNER);
        for (Long target : List.of(idOf(other), ownerId)) {
            mockMvc.perform(post("/api/v1/admin/admin-users/" + target + "/reset-password").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", "Reset#Pass-2026y"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/admin/admin-users/" + target).header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v1/admin/admin-users/" + target + "/status").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                    .andExpect(status().isForbidden());
            expect(token, "DELETE", "/api/v1/admin/admin-users/" + target, 403);
        }
        expect(token, "GET", "/api/v1/admin/admin-users", 403);
        expect(token, "GET", "/api/v1/admin/admin-users/options", 403);
        // The user-initiated change-password endpoint no longer exists (for anyone)
        for (String t : List.of(token, ownerToken)) {
            mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(t))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("currentPassword", INITIAL_PASSWORD, "newPassword", "Another#Pass-2026v"))))
                    .andExpect(status().isNotFound());
        }
        loginExpect(manager, INITIAL_PASSWORD, 200);
        assertTrue(adminUserRepository.findById(ownerId).orElseThrow().isActive());
    }


    @Test
    void ownerCanSetTheirOwnPassword_fromUserManagement() throws Exception {
        String second = createUser("OWNER", null, "America/Chicago");
        String token = activate(second);
        mockMvc.perform(post("/api/v1/admin/admin-users/" + idOf(second) + "/reset-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", "Owner#Changed-2026"))))
                .andExpect(status().isOk());
        expect(token, "GET", "/api/v1/admin/admin-users", 401);   // own sessions signed out too
        loginExpect(second, "Owner#Changed-2026", 200);
    }


    @Test
    void noApiResponseContainsPasswordsOrHashes() throws Exception {
        String user = createUser("MANAGER", null, "Asia/Kolkata");
        List<String> bodies = new ArrayList<>();
        bodies.add(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", user, "password", INITIAL_PASSWORD)))).andReturn().getResponse().getContentAsString());
        bodies.add(mockMvc.perform(get("/api/v1/admin/admin-users/options").header("Authorization", bearer(ownerToken))).andReturn().getResponse().getContentAsString());
        bodies.add(mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(ownerToken))).andReturn().getResponse().getContentAsString());
        bodies.add(mockMvc.perform(get("/api/v1/admin/admin-users").header("Authorization", bearer(ownerToken))).andReturn().getResponse().getContentAsString());
        bodies.add(mockMvc.perform(post("/api/v1/admin/admin-users/" + idOf(user) + "/reset-password").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", "Reset#Pass-2026y")))).andReturn().getResponse().getContentAsString());
        bodies.add(mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(ownerToken))).andReturn().getResponse().getContentAsString());
        for (String body : bodies) {
            assertFalse(body.contains("$2a$") || body.contains("$2b$") || body.contains("$2y$"), "no BCrypt hash: " + body);
            assertFalse(body.toLowerCase(Locale.ROOT).contains("passwordhash"), "no hash field: " + body);
            assertFalse(body.contains(INITIAL_PASSWORD) || body.contains("Reset#Pass-2026y") || body.contains(OWNER_PASSWORD), "no plaintext password");
        }
    }

    @Test
    void startupWorksWithoutBootstrapVariables_onceAnOwnerExists() {
        AdminUser before = adminUserRepository.findByUsername(OWNER).orElseThrow();
        Object user = ReflectionTestUtils.getField(bootstrapService, "bootstrapUsername");
        Object pass = ReflectionTestUtils.getField(bootstrapService, "bootstrapPassword");
        try {
            ReflectionTestUtils.setField(bootstrapService, "bootstrapUsername", "");
            ReflectionTestUtils.setField(bootstrapService, "bootstrapPassword", "");
            assertDoesNotThrow(() -> bootstrapService.run());
        } finally {
            ReflectionTestUtils.setField(bootstrapService, "bootstrapUsername", user);
            ReflectionTestUtils.setField(bootstrapService, "bootstrapPassword", pass);
        }
        assertEquals(before.getPasswordHash(), adminUserRepository.findByUsername(OWNER).orElseThrow().getPasswordHash());
    }

    @Test
    void credentialsComeFromTheDatabase_andOwnerResetsRevokeOldSessions() throws Exception {
        String user = createUser("MANAGER", null, "Asia/Kolkata");
        String token = login(user, INITIAL_PASSWORD).path("data").path("token").asText();
        AdminUser created = adminUserRepository.findByUsername(user).orElseThrow();
        assertTrue(created.getPasswordHash().startsWith("$2"), "initial password stored only as BCrypt");
        assertNotNull(created.getPasswordChangedAt());
        mockMvc.perform(post("/api/v1/admin/admin-users/" + created.getId() + "/reset-password").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", "Changed#Pass-2026z"))))
                .andExpect(status().isOk());
        expect(token, "GET", "/api/v1/admin/enquiries", 401);    // old session revoked
        loginExpect(user, INITIAL_PASSWORD, 401);                // old password no longer works
        loginExpect(user, "Changed#Pass-2026z", 200);            // verified against the stored BCrypt hash
    }


    @Test
    void bootstrapNeverOverwritesExistingCredentials() {
        AdminUser before = adminUserRepository.findByUsername(OWNER).orElseThrow();
        Object original = ReflectionTestUtils.getField(bootstrapService, "bootstrapPassword");
        try {
            ReflectionTestUtils.setField(bootstrapService, "bootstrapPassword", "A-Different#Env-Pass1");
            bootstrapService.run();
        } finally {
            ReflectionTestUtils.setField(bootstrapService, "bootstrapPassword", original);
        }
        AdminUser after = adminUserRepository.findByUsername(OWNER).orElseThrow();
        assertEquals(before.getPasswordHash(), after.getPasswordHash());
        assertFalse(passwordEncoder.matches("A-Different#Env-Pass1", after.getPasswordHash()));
    }

    @Test
    void invalidCredentials_andLockout() throws Exception {
        loginExpect("no_such_user_" + suffix(), "whatever#1A", 401);
        String user = createUser("MANAGER", null, "Asia/Kolkata");
        for (int i = 0; i < 5; i++) loginExpect(user, "Wrong#Pass-0000", 401);
        loginExpect(user, INITIAL_PASSWORD, 423); // locked even with the right password
    }

    // ---------------------------------------------------------------- helpers

    /** Signs in with the owner-set initial password and returns the session token (no change step). */
    private String activate(String username) throws Exception {
        return login(username, INITIAL_PASSWORD).path("data").path("token").asText();
    }

    private String createUser(String role, List<String> permissions, String timezone) throws Exception {
        String username = "it_" + role.toLowerCase(Locale.ROOT) + "_" + suffix();
        created.add(username);
        mockMvc.perform(post("/api/v1/admin/admin-users").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(userBody(username, role, permissions, timezone))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value(role))
                .andExpect(jsonPath("$.data.mustChangePassword").doesNotExist());
        return username;
    }

    private Map<String, Object> userBody(String username, String role, List<String> permissions, String timezone) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("displayName", "IT " + username);
        body.put("email", username + "@example.com");
        body.put("role", role);
        body.put("permissions", permissions);
        body.put("timezone", timezone);
        body.put("initialPassword", INITIAL_PASSWORD);
        return body;
    }

    private void update(Long id, String role, List<String> permissions, int expected) throws Exception {
        AdminUser u = adminUserRepository.findById(id).orElseThrow();
        Map<String, Object> body = new HashMap<>();
        body.put("displayName", u.getDisplayName());
        body.put("email", u.getEmail());
        body.put("role", role);
        body.put("permissions", permissions);
        body.put("timezone", u.getTimezone());
        mockMvc.perform(put("/api/v1/admin/admin-users/" + id).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is(expected));
    }

    private void setEnabled(Long id, boolean enabled, int expected) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/admin-users/" + id + "/status").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", enabled))))
                .andExpect(status().is(expected));
    }

    private void expect(String token, String method, String path, int expected) throws Exception {
        var request = switch (method) {
            case "DELETE" -> delete(path);
            default -> get(path);
        };
        mockMvc.perform(request.header("Authorization", bearer(token))).andExpect(status().is(expected));
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void loginExpect(String username, String password, int expected) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().is(expected));
    }

    private Long idOf(String username) {
        return adminUserRepository.findByUsername(username).orElseThrow().getId();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
