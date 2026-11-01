package com.jovensbuzz;

import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.integration.googlesheets.GoogleSheetsService;
import com.jovensbuzz.integration.googlesheets.SheetRow;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GoogleSheetsServiceTest {

    private GoogleSheetsService googleSheetsService;
    private HttpServer mockServer;
    private String serverUrl;
    private final AtomicReference<String> lastReceivedRequestBody = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatusCode = new AtomicReference<>(200);
    private final AtomicReference<String> responseResponseBody = new AtomicReference<>("{\"success\":true,\"message\":\"Enquiry appended successfully\"}");

    @BeforeEach
    void setUp() throws IOException {
        googleSheetsService = new GoogleSheetsService();
        ReflectionTestUtils.setField(googleSheetsService, "connectTimeoutMs", 3000);
        ReflectionTestUtils.setField(googleSheetsService, "readTimeoutMs", 3000);

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/exec", exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            lastReceivedRequestBody.set(new String(bytes, StandardCharsets.UTF_8));

            byte[] resp = responseResponseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatusCode.get(), resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockServer.start();
        serverUrl = "http://localhost:" + mockServer.getAddress().getPort() + "/exec";
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    private SheetRow createTestRow() {
        Enquiry enquiry = new Enquiry();
        enquiry.setId(10L);
        enquiry.setPublicReference("JB-2026-TEST01");
        enquiry.setFullName("John Doe");
        enquiry.setFirstName("John");
        enquiry.setLastName("Doe");
        enquiry.setEmail("john@example.com");
        enquiry.setPhone("+12015550123");
        enquiry.setPhoneCountry("US");
        enquiry.setCompanyName("Acme Corp");
        enquiry.setServiceInterest("Brand Identity");
        enquiry.setBudget("$5,000");
        enquiry.setMessage("We want to build a modern brand.");
        enquiry.setStatus(EnquiryStatus.NEW);
        enquiry.setCreatedAt(Instant.parse("2026-10-30T10:00:00Z"));
        return SheetRow.fromEnquiry(enquiry);
    }

    @Test
    void isConfigured_returnsTrueWhenWebAppUrlIsSet() {
        assertFalse(googleSheetsService.isConfigured());

        ReflectionTestUtils.setField(googleSheetsService, "webAppUrl", "https://script.google.com/macros/s/test/exec");
        assertTrue(googleSheetsService.isConfigured());
        assertTrue(googleSheetsService.isWebAppConfigured());
    }

    @Test
    void appendIfAbsent_sendsAllExpectedFieldsToWebApp() throws Exception {
        ReflectionTestUtils.setField(googleSheetsService, "webAppUrl", serverUrl);
        ReflectionTestUtils.setField(googleSheetsService, "secretToken", "TEST_SECRET_TOKEN");
        ReflectionTestUtils.setField(googleSheetsService, "spreadsheetId", "TEST_SHEET_ID");
        ReflectionTestUtils.setField(googleSheetsService, "spreadsheetName", "jovens_Buzz_website");
        ReflectionTestUtils.setField(googleSheetsService, "worksheetName", "Enquiries");

        SheetRow row = createTestRow();
        String result = googleSheetsService.appendIfAbsent(row);

        assertEquals("apps_script:success", result);

        String sentBody = lastReceivedRequestBody.get();
        assertNotNull(sentBody);
        assertTrue(sentBody.contains("\"token\":\"TEST_SECRET_TOKEN\""));
        assertTrue(sentBody.contains("\"firstName\":\"John\""));
        assertTrue(sentBody.contains("\"lastName\":\"Doe\""));
        assertTrue(sentBody.contains("\"email\":\"john@example.com\""));
        assertTrue(sentBody.contains("\"countryCode\":\"+1\""));
        assertTrue(sentBody.contains("\"phone\":\"201-555-0123\""));
        assertTrue(sentBody.contains("\"company\":\"Acme Corp\""));
        assertTrue(sentBody.contains("\"service\":\"Brand Identity\""));
        assertTrue(sentBody.contains("\"message\":\"We want to build a modern brand.\""));
        assertTrue(sentBody.contains("\"reference\":\"JB-2026-TEST01\""));
        assertFalse(sentBody.contains("platforms"));
        assertFalse(sentBody.contains("budget"));
        assertTrue(sentBody.contains("\"spreadsheetId\":\"TEST_SHEET_ID\""));
        assertTrue(sentBody.contains("\"worksheet\":\"Enquiries\""));
    }

    @Test
    void appendIfAbsent_handlesInvalidToken_andThrowsException() {
        ReflectionTestUtils.setField(googleSheetsService, "webAppUrl", serverUrl);
        ReflectionTestUtils.setField(googleSheetsService, "secretToken", "INVALID_TOKEN");
        responseResponseBody.set("{\"success\":false,\"message\":\"Unauthorized\"}");

        SheetRow row = createTestRow();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> googleSheetsService.appendIfAbsent(row));
        assertTrue(ex.getMessage().contains("Unauthorized"));
    }

    @Test
    void appendIfAbsent_detectsExistingRowAndAvoidsDuplicate() throws Exception {
        ReflectionTestUtils.setField(googleSheetsService, "webAppUrl", serverUrl);
        ReflectionTestUtils.setField(googleSheetsService, "secretToken", "TEST_TOKEN");
        responseResponseBody.set("{\"success\":true,\"message\":\"Already exists\",\"row\":7}");

        SheetRow row = createTestRow();
        String result = googleSheetsService.appendIfAbsent(row);

        assertEquals("existing:7", result);
    }

    @Test
    void fromEnquiry_splitsLegacyFullNameAndPhone() {
        Enquiry enquiry = new Enquiry();
        enquiry.setPublicReference("JB-2026-LEGACY");
        enquiry.setFullName("Mary Ann Smith");
        enquiry.setPhone("+916379362630");
        enquiry.setServiceInterest("Content Creation");
        enquiry.setCreatedAt(Instant.parse("2026-10-30T10:00:00Z"));

        SheetRow row = SheetRow.fromEnquiry(enquiry);

        assertEquals("Mary", row.firstName());
        assertEquals("Ann Smith", row.lastName());
        assertEquals("+91", row.countryCode());
        assertEquals("63793 62630", row.phone());
        assertEquals(SheetRow.HEADER.size(), row.values().size());
    }

    @Test
    void appendIfAbsent_treatsHtmlResponseAsFailure() {
        ReflectionTestUtils.setField(googleSheetsService, "webAppUrl", serverUrl);
        ReflectionTestUtils.setField(googleSheetsService, "secretToken", "TEST_TOKEN");
        responseResponseBody.set("<html><body><div>Script function not found: doPost</div></body></html>");

        SheetRow row = createTestRow();
        IOException ex = assertThrows(IOException.class, () -> googleSheetsService.appendIfAbsent(row));
        assertTrue(ex.getMessage().contains("Script function not found"));
    }

    @Test
    void appendIfAbsent_handlesHttpServerError_andThrowsIOException() {
        ReflectionTestUtils.setField(googleSheetsService, "webAppUrl", serverUrl);
        ReflectionTestUtils.setField(googleSheetsService, "secretToken", "TEST_TOKEN");
        responseStatusCode.set(500);
        responseResponseBody.set("Internal Error");

        SheetRow row = createTestRow();
        assertThrows(IOException.class, () -> googleSheetsService.appendIfAbsent(row));
    }
}
