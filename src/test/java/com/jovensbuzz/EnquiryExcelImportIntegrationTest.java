package com.jovensbuzz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.repository.AuditLogRepository;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.integration.email.repository.EmailNotificationJobRepository;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Excel import against the real test database: preview validation, duplicate detection,
 * all-or-nothing commit, time zone conversion and file safety limits.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnquiryExcelImportIntegrationTest {

    static final String[] HEADERS = {"First Name", "Last Name", "Email", "Country", "Phone", "Company", "Service", "Message", "Status", "Submitted At"};

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EnquiryRepository enquiryRepository;
    @Autowired private EmailNotificationJobRepository emailJobRepository;
    @Autowired private IntegrationSyncJobRepository syncJobRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AdminUserRepository adminUserRepository;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String token;

    @BeforeEach
    void signIn() throws Exception {
        adminUserRepository.findByUsername("test_admin").ifPresent(o -> { o.setTimezone("America/Chicago"); o.setMustChangePassword(false); adminUserRepository.save(o); });
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test_admin\",\"password\":\"Test-Password#2026\"}"))
                .andExpect(status().isOk()).andReturn();
        token = objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    @AfterEach
    void cleanUp() {
        enquiryRepository.deleteAll(enquiryRepository.findAll().stream()
                .filter(e -> e.getEmail() != null && e.getEmail().contains(tag)).toList());
    }

    @Test
    void preview_flagsEveryProblem_andSavesNothing() throws Exception {
        Enquiry existing = new Enquiry();
        existing.setPublicReference("JB-T-" + tag.toUpperCase(Locale.ROOT));
        existing.setFullName("Already There");
        existing.setEmail("dup-" + tag + "@example.com");
        existing.setPhone("+12015550123");
        existing.setServiceInterest("Other");
        existing.setMessage("Existing message");
        existing.setStatus(com.jovensbuzz.enquiry.entity.EnquiryStatus.NEW);
        existing.setSource("WEBSITE_CONTACT");
        existing.setSubmissionFingerprint(com.jovensbuzz.enquiry.service.EnquiryService.fingerprint(existing.getEmail(), "Other", "Existing message"));
        enquiryRepository.save(existing);
        long before = enquiryRepository.count();

        byte[] file = xlsx(List.of(
                row("Ana", "Valid", mail("ana"), "US", "(201) 555-0123", "Acme", "Other", "Hello there", "NEW", "2026-10-31 09:30"),
                row("Raj", "Valid", mail("raj"), "", "+91 98765 43210", "", "Content Creation", "", "", ""),
                row("Bad", "Email", "not-an-email", "US", "(201) 555-0123", "", "Other", "", "", ""),
                row("Bad", "Phone", mail("phone"), "US", "12345", "", "Other", "", "", ""),
                row("", "NoFirst", mail("nofirst"), "US", "(201) 555-0123", "", "Other", "", "", ""),
                row("Bad", "Service", mail("svc"), "US", "(201) 555-0123", "", "Underwater Basket Weaving", "", "", ""),
                row("Ana", "Again", mail("ana"), "US", "(201) 555-0124", "", "Other", "Hello there", "", ""),
                row("Bad", "Date", mail("date"), "US", "(201) 555-0123", "", "Other", "", "", "31/31/2026"),
                row("Future", "Date", mail("future"), "US", "(201) 555-0123", "", "Other", "", "", "2099-01-01 10:00"),
                row("Already", "There", "dup-" + tag + "@example.com", "US", "(201) 555-0123", "", "Other", "Existing message", "", ""),
                row("Wrong", "Country", mail("ctry"), "FR", "+33 1 23 45 67 89", "", "Other", "", "", "")));

        JsonNode preview = preview(file, "leads.xlsx").path("data");
        assertEquals(11, preview.path("total").asInt());
        assertEquals(2, preview.path("valid").asInt());
        assertEquals(2, preview.path("duplicates").asInt());
        assertEquals(7, preview.path("invalid").asInt());
        assertEquals("America/Chicago", preview.path("timezone").asText());
        Map<String, JsonNode> byLast = new HashMap<>();
        preview.path("rows").forEach(r -> byLast.put(r.path("values").path("lastName").asText(), r));
        assertEquals("VALID", byLast.get("Valid").path("state").asText());
        assertErrors(byLast.get("Email"), "Invalid email");
        assertErrors(byLast.get("Phone"), "Invalid phone number for US");
        assertErrors(byLast.get("NoFirst"), "First name is required");
        assertErrors(byLast.get("Service"), "Unknown service");
        assertEquals("DUPLICATE", byLast.get("Again").path("state").asText());
        assertErrors(byLast.get("Again"), "Same as row 2 in this file");
        assertErrors(preview.path("rows").get(7), "Invalid Submitted At");      // two rows share the last name "Date"
        assertErrors(preview.path("rows").get(8), "cannot be in the future");
        assertEquals("DUPLICATE", byLast.get("There").path("state").asText());
        assertEquals(existing.getPublicReference(), byLast.get("There").path("duplicateOf").asText());
        assertErrors(byLast.get("Country"), "Country must be one of");
        assertEquals(before, enquiryRepository.count(), "preview never saves");
    }

    @Test
    void commit_isAllOrNothing_convertsTimezone_sendsNoEmail_andIsAudited() throws Exception {
        byte[] file = xlsx(List.of(
                row("Ana", "Import", mail("ana"), "US", "(201) 555-0123", "Acme", "Other", "Hello there", "CONTACTED", "2026-10-31 09:30"),
                row("Raj", "Import", mail("raj"), "", "+91 98765 43210", "", "Content Creation", "", "", "2026-03-08 03:30"),
                row("Bad", "Email", "nope", "US", "(201) 555-0123", "", "Other", "", "", "")));
        JsonNode rows = preview(file, "leads.xlsx").path("data").path("rows");
        long before = enquiryRepository.count();

        // Correcting the invalid row in the browser and re-validating makes it valid (nothing saved yet)
        Map<String, Object> corrected = new HashMap<>(objectMapper.convertValue(rows.get(2).path("values"), Map.class));
        corrected.put("email", "fixed-" + tag + "@example.com");
        MvcResult revalidated = mockMvc.perform(post("/api/v1/admin/enquiries/import/validate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rows", List.of(corrected), "filename", "leads.xlsx"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(1))
                .andReturn();
        assertNotNull(revalidated);
        assertEquals(before, enquiryRepository.count(), "validation never saves");

        // Including the invalid row: rejected, nothing saved
        mockMvc.perform(post("/api/v1/admin/enquiries/import/commit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(commitBody(rows, List.of(0, 1, 2), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1));
        assertEquals(before, enquiryRepository.count());

        // Skip the invalid row: both valid rows saved in one go
        MvcResult done = mockMvc.perform(post("/api/v1/admin/enquiries/import/commit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(commitBody(rows, List.of(0, 1), 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imported").value(2))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andReturn();
        assertEquals(before + 2, enquiryRepository.count());
        List<String> refs = new ArrayList<>();
        objectMapper.readTree(done.getResponse().getContentAsString()).path("data").path("references").forEach(n -> refs.add(n.asText()));

        Enquiry ana = enquiryRepository.findByPublicReference(refs.get(0)).orElseThrow();
        assertEquals(Instant.parse("2026-10-31T14:30:00Z"), ana.getCreatedAt(), "09:30 CDT = 14:30 UTC");
        assertEquals("ADMIN_IMPORT", ana.getSource());
        assertEquals("CONTACTED", ana.getStatus().name());
        assertEquals("+12015550123", ana.getPhone());
        assertEquals("Ana", ana.getFirstName());
        Enquiry raj = enquiryRepository.findByPublicReference(refs.get(1)).orElseThrow();
        assertEquals(Instant.parse("2026-03-08T08:30:00Z"), raj.getCreatedAt(), "03:30 on the DST day is already CDT (UTC-5)");
        assertEquals("IN", raj.getPhoneCountry());
        assertEquals("+919876543210", raj.getPhone());

        Set<Long> ids = Set.of(ana.getId(), raj.getId());
        assertTrue(emailJobRepository.findAll().stream().noneMatch(j -> ids.contains(j.getEnquiry().getId())), "imports never email anyone");
        assertTrue(syncJobRepository.findAll().stream().noneMatch(j -> ids.contains(j.getEnquiry().getId())), "not sent to Sheets unless requested");
        assertTrue(auditLogRepository.findAll().stream().anyMatch(a -> "IMPORT_ENQUIRIES".equals(a.getAction())
                && a.getSafeMetadata() != null && a.getSafeMetadata().contains("Imported 2") && a.getSafeMetadata().contains("skipped 1")));

        // Importing the same file again: the rows are now duplicates of what was just saved
        JsonNode again = preview(file, "leads.xlsx").path("data");
        assertEquals(2, again.path("duplicates").asInt());
    }

    @Test
    void xlsFormat_isSupported() throws Exception {
        Workbook wb = new HSSFWorkbook();
        fill(wb, List.<String[]>of(row("Old", "Format", mail("xls"), "GB", "07400 123456", "", "Other", "", "", "")));
        JsonNode preview = preview(bytes(wb), "legacy.xls").path("data");
        assertEquals(1, preview.path("valid").asInt());
    }

    @Test
    void badFiles_areRejectedWithClearMessages() throws Exception {
        expectRejected(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()), "Unsupported file type");
        expectRejected(new MockMultipartFile("file", "fake.xlsx", "application/octet-stream", "not really excel".getBytes()), "could not be read");
        expectRejected(new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", xlsx(List.of())), "no enquiry rows");
        Workbook noPhone = new XSSFWorkbook();
        Row h = noPhone.createSheet("Enquiries").createRow(0);
        String[] cols = {"First Name", "Last Name", "Email", "Service"};
        for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
        noPhone.getSheetAt(0).createRow(1).createCell(0).setCellValue("x");
        expectRejected(new MockMultipartFile("file", "nophone.xlsx", "application/octet-stream", bytes(noPhone)), "Missing required column(s): Phone");
        List<String[]> many = new ArrayList<>();
        for (int i = 0; i < 5001; i++) many.add(row("Bulk", "Row" + i, "b" + i + "-" + tag + "@example.com", "US", "(201) 555-0123", "", "Other", "", "", ""));
        expectRejected(new MockMultipartFile("file", "big.xlsx", "application/octet-stream", xlsx(many)), "more than 5000 rows");
        mockMvc.perform(multipart("/api/v1/admin/enquiries/import/preview").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void template_downloadsWithHeadersAndInstructions() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/admin/enquiries/import/template").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            Row header = wb.getSheet("Enquiries").getRow(0);
            for (int i = 0; i < HEADERS.length; i++) assertEquals(HEADERS[i], header.getCell(i).getStringCellValue());
            assertNotNull(wb.getSheet("Instructions"));
            assertFalse(wb.getSheet("Enquiries").getDataValidations().isEmpty(), "country/status dropdowns");
        }
    }

    // ---------------------------------------------------------------- helpers

    private String mail(String who) {
        return who + "-" + tag + "@example.com";
    }

    static String[] row(String... values) {
        return values;
    }

    static byte[] xlsx(List<String[]> rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        fill(wb, rows);
        return bytes(wb);
    }

    static void fill(Workbook wb, List<String[]> rows) {
        Sheet sheet = wb.createSheet("Enquiries");
        Row h = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) h.createCell(i).setCellValue(HEADERS[i]);
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows.get(r).length; c++) row.createCell(c).setCellValue(rows.get(r)[c]);
        }
    }

    static byte[] bytes(Workbook wb) throws Exception {
        try (wb) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode preview(byte[] content, String name) throws Exception {
        MvcResult r = mockMvc.perform(multipart("/api/v1/admin/enquiries/import/preview")
                        .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private void expectRejected(MockMultipartFile file, String message) throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/enquiries/import/preview").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(message)));
    }

    private String commitBody(JsonNode rows, List<Integer> indexes, int skipped) throws Exception {
        List<JsonNode> values = indexes.stream().map(i -> rows.get(i).path("values")).toList();
        return objectMapper.writeValueAsString(Map.of("rows", values, "addToGoogleSheets", false, "filename", "leads.xlsx", "skipped", skipped));
    }

    private static void assertErrors(JsonNode row, String expected) {
        assertNotNull(row, "row present");
        String errors = row.path("errors").toString();
        assertTrue(errors.contains(expected), "expected '" + expected + "' in " + errors);
    }
}
