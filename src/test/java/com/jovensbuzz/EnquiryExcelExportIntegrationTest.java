package com.jovensbuzz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovensbuzz.admin.entity.AdminPermission;
import com.jovensbuzz.admin.entity.AdminRole;
import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Excel export against the real test database: filters, sort, all pages, formatting and
 * time zone conversion (including both 2026 US Central daylight-saving transitions).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnquiryExcelExportIntegrationTest {

    private static final int HEADER_ROW = 7;          // 0-based row of the column headings
    private static final String OWNER = "test_admin";
    private static final String OWNER_PASSWORD = "Test-Password#2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EnquiryRepository enquiryRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final String marker = "XLTEST-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> enquiryIds = new ArrayList<>();
    private String indiaManager;
    private String ownerToken;

    @BeforeEach
    void data() throws Exception {
        ownerToken = token(OWNER, OWNER_PASSWORD);
        adminUserRepository.findByUsername(OWNER).ifPresent(o -> { o.setTimezone("America/Chicago"); o.setMustChangePassword(false); adminUserRepository.save(o); });
        // Around the 2026-03-08 CST->CDT jump (08:00Z) and the 2026-11-01 CDT->CST fall back (07:00Z)
        save("A", EnquiryStatus.NEW, "Content Creation", "2026-03-08T07:30:00Z");       // 01:30 CST
        save("B", EnquiryStatus.CONTACTED, "Website Development", "2026-03-08T09:00:00Z"); // 04:00 CDT
        save("C", EnquiryStatus.NEW, "Website Development", "2026-11-01T06:30:00Z");     // 01:30 CDT
        save("D", EnquiryStatus.NEW, "Content Creation", "2026-11-01T07:30:00Z");        // 01:30 CST
    }

    @AfterEach
    void cleanUp() {
        enquiryRepository.deleteAllById(enquiryIds);
        if (indiaManager != null) adminUserRepository.findByUsername(indiaManager).ifPresent(adminUserRepository::delete);
    }

    @Test
    void exportsOnlyFilteredRows_inSortOrder_withReportDetails() throws Exception {
        Sheet all = export(ownerToken, "search=" + marker + "&sortBy=createdAt&sortDir=asc");
        assertEquals(List.of("A", "B", "C", "D"), names(all));
        assertTrue(text(all, 5).contains("4"), "total record count");
        assertTrue(text(all, 3).contains("Search = \"" + marker + "\""));
        assertTrue(text(all, 4).contains("ascending"));

        assertEquals(List.of("D", "C", "B", "A"), names(export(ownerToken, "search=" + marker + "&sortDir=desc")));
        assertEquals(List.of("A", "C", "D"), names(export(ownerToken, "search=" + marker + "&status=NEW&sortDir=asc")));
        assertEquals(List.of("B", "C"), names(export(ownerToken, "search=" + marker + "&service=Website%20Development&sortDir=asc")));
        Sheet multi = export(ownerToken, "search=" + marker + "&status=NEW&service=Website%20Development");
        assertEquals(List.of("C"), names(multi));
        assertTrue(text(multi, 3).contains("Status = New") && text(multi, 3).contains("Service = Website Development"));
        Sheet range = export(ownerToken, "search=" + marker + "&startDate=2026-11-01T05:00:00Z&endDate=2026-11-02T04:59:59Z&sortDir=asc");
        assertEquals(List.of("C", "D"), names(range));
        assertTrue(text(range, 3).contains("Date range = Nov 1, 2026"));
        Sheet none = export(ownerToken, "search=" + marker + "&status=SPAM");
        assertTrue(text(none, 5).contains("0"));
    }

    @Test
    void formatting_headersFrozenFilteredStyledAndDatesAreRealExcelValues() throws Exception {
        XSSFSheet sheet = (XSSFSheet) export(ownerToken, "search=" + marker + "&sortDir=asc");
        Row header = sheet.getRow(HEADER_ROW);
        assertEquals("Enquiry ID", header.getCell(0).getStringCellValue());
        assertEquals("Submitted Date (America/Chicago)", header.getCell(9).getStringCellValue());
        assertEquals("Google Sheets Sync Status", header.getCell(11).getStringCellValue());
        assertTrue(sheet.getWorkbook().getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold());
        assertEquals(FillPatternType.SOLID_FOREGROUND, header.getCell(0).getCellStyle().getFillPattern());
        assertEquals(BorderStyle.THIN, sheet.getRow(HEADER_ROW + 1).getCell(1).getCellStyle().getBorderBottom());
        assertEquals(HEADER_ROW + 1, sheet.getPaneInformation().getHorizontalSplitPosition(), "header row frozen");
        assertEquals("A8:L12", sheet.getCTWorksheet().getAutoFilter().getRef(), "Excel filter on header + data");
        Cell date = sheet.getRow(HEADER_ROW + 1).getCell(9);
        assertEquals(CellType.NUMERIC, date.getCellType());
        assertTrue(DateUtil.isCellDateFormatted(date));
        assertEquals("Not queued", sheet.getRow(HEADER_ROW + 1).getCell(11).getStringCellValue());
        assertTrue(sheet.getColumnWidth(6) > sheet.getColumnWidth(0), "message column is wider");
    }

    @Test
    void chicagoTimes_followDaylightSaving() throws Exception {
        Sheet sheet = export(ownerToken, "search=" + marker + "&sortDir=asc");
        assertEquals(LocalDate.of(2026, 3, 8), dateAt(sheet, 0));
        assertEquals(LocalTime.of(1, 30), timeAt(sheet, 0));   // CST (UTC-6)
        assertEquals(LocalTime.of(4, 0), timeAt(sheet, 1));    // CDT (UTC-5), 90 minutes later in UTC
        assertEquals(LocalDate.of(2026, 11, 1), dateAt(sheet, 2));
        assertEquals(LocalTime.of(1, 30), timeAt(sheet, 2));   // 06:30Z = 01:30 CDT
        assertEquals(LocalTime.of(1, 30), timeAt(sheet, 3));   // 07:30Z = 01:30 CST (the repeated hour)
        assertTrue(text(sheet, 2).contains("America/Chicago (CST)"), "label reflects the zone in effect now (Nov 2026)");
    }

    @Test
    void indiaManager_seesIndiaTime_forTheSameEvents() throws Exception {
        indiaManager = "it_xl_" + UUID.randomUUID().toString().substring(0, 6);
        AdminUser manager = new AdminUser(indiaManager, passwordEncoder.encode("Kolkata#Pass-2026"), "India Manager",
                indiaManager + "@example.com", AdminRole.MANAGER.authority());
        manager.setTimezone("Asia/Kolkata");
        manager.setAssignedPermissions(EnumSet.of(AdminPermission.ENQUIRIES_VIEW, AdminPermission.ENQUIRIES_EXPORT));
        adminUserRepository.save(manager);

        Sheet sheet = export(token(indiaManager, "Kolkata#Pass-2026"), "search=" + marker + "&sortDir=asc");
        assertEquals("Submitted Time (Asia/Kolkata)", sheet.getRow(HEADER_ROW).getCell(10).getStringCellValue());
        assertEquals(LocalTime.of(13, 0), timeAt(sheet, 0));   // 07:30Z + 05:30
        assertEquals(LocalTime.of(14, 30), timeAt(sheet, 1));
        assertEquals(LocalTime.of(12, 0), timeAt(sheet, 2));
        assertEquals(LocalTime.of(13, 0), timeAt(sheet, 3));
        assertTrue(text(sheet, 2).contains("IST"));
    }

    @Test
    void exportsEveryMatchingRecord_acrossListPages() throws Exception {
        String bulk = "XLBULK-" + UUID.randomUUID().toString().substring(0, 8);
        List<Enquiry> many = new ArrayList<>();
        Instant base = Instant.parse("2026-06-01T12:00:00Z");
        for (int i = 0; i < 1205; i++) many.add(enquiry(bulk + "-" + i, bulk, EnquiryStatus.NEW, "Other", base.plusSeconds(i)));
        enquiryRepository.saveAll(many).forEach(e -> enquiryIds.add(e.getId()));

        mockMvc.perform(get("/api/v1/admin/enquiries?search=" + bulk + "&size=10").header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data.totalElements").value(1205))
                .andExpect(jsonPath("$.data.content.length()").value(10));
        MvcResult result = mockMvc.perform(get("/api/v1/admin/enquiries/export/excel?search=" + bulk)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Export-Count", "1205"))
                .andReturn();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertEquals(HEADER_ROW + 1205, wb.getSheetAt(0).getLastRowNum(), "all 1205 rows exported (2 pages of 1000)");
        }
    }

    // ---------------------------------------------------------------- helpers

    private void save(String name, EnquiryStatus status, String service, String createdAt) {
        enquiryIds.add(enquiryRepository.save(enquiry(name, marker, status, service, Instant.parse(createdAt))).getId());
    }

    private Enquiry enquiry(String name, String company, EnquiryStatus status, String service, Instant createdAt) {
        Enquiry e = new Enquiry();
        e.setPublicReference("JB-T-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT));
        e.setFullName(name);
        e.setEmail(name.toLowerCase(Locale.ROOT) + "@example.com");
        e.setPhone("+12015550123");
        e.setPhoneCountry("US");
        e.setCompanyName(company);
        e.setServiceInterest(service);
        e.setMessage("Export test " + name);
        e.setStatus(status);
        e.setSource("WEBSITE_CONTACT");
        e.setCreatedAt(createdAt);
        return e;
    }

    /** query is "key=value&..." with %-encoded values; sent as real request parameters. */
    private Sheet export(String token, String query) throws Exception {
        var request = get("/api/v1/admin/enquiries/export/excel").header("Authorization", "Bearer " + token);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            request.param(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            return wb.getSheetAt(0); // the in-memory sheet stays readable after closing the package
        }
    }

    private static List<String> names(Sheet sheet) {
        List<String> names = new ArrayList<>();
        for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getCell(1) != null) names.add(row.getCell(1).getStringCellValue());
        }
        return names;
    }

    private static String text(Sheet sheet, int row) {
        Row r = sheet.getRow(row);
        StringBuilder sb = new StringBuilder();
        if (r != null) r.forEach(c -> sb.append(c.getCellType() == CellType.STRING ? c.getStringCellValue() : "").append(' '));
        return sb.toString();
    }

    private static LocalDate dateAt(Sheet sheet, int dataRow) {
        return sheet.getRow(HEADER_ROW + 1 + dataRow).getCell(9).getLocalDateTimeCellValue().toLocalDate();
    }

    private static LocalTime timeAt(Sheet sheet, int dataRow) {
        double fraction = sheet.getRow(HEADER_ROW + 1 + dataRow).getCell(10).getNumericCellValue();
        return LocalTime.ofSecondOfDay(Math.round(fraction * 86_400));
    }

    private String token(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
