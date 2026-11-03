package com.jovensbuzz.enquiry.service;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.response.PagedResponse;
import com.jovensbuzz.common.util.TimeZones;
import com.jovensbuzz.common.validation.InternationalPhoneValidator;
import com.jovensbuzz.enquiry.dto.EnquiryResponseDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Filter-aware Excel export of enquiries. Uses exactly the same filters and query as the enquiry list
 * (EnquiryService#getEnquiries), keeps the list's sort order and walks every page, so the workbook
 * contains all matching records, not just the visible page. Dates are converted to the exporting
 * user's IANA time zone; rows are streamed (SXSSF) to keep memory flat for large exports.
 */
@Service
public class EnquiryExcelExportService {

    public static final int MAX_EXPORT_ROWS = 100_000;
    private static final int PAGE_SIZE = 1_000;
    static final String[] COLUMNS = {
            "Enquiry ID", "Name", "Email", "Phone", "Company", "Service", "Message", "Status",
            "Assigned To", "Submitted Date", "Submitted Time", "Google Sheets Sync Status"};
    private static final int[] WIDTHS = {18, 26, 32, 20, 26, 26, 60, 13, 22, 16, 15, 24};
    /** Header row index (0-based); report details sit above it. */
    static final int HEADER_ROW = 7;

    private static final Map<String, String> SORT_LABELS = Map.of(
            "createdAt", "Submitted date", "fullName", "Name", "email", "Email", "companyName", "Company",
            "serviceInterest", "Service", "status", "Status", "publicReference", "Enquiry ID");

    private final EnquiryService enquiryService;
    private final AdminUserRepository adminUserRepository;
    private final AuditLogService auditLogService;

    public EnquiryExcelExportService(EnquiryService enquiryService, AdminUserRepository adminUserRepository,
                                     AuditLogService auditLogService) {
        this.enquiryService = enquiryService;
        this.adminUserRepository = adminUserRepository;
        this.auditLogService = auditLogService;
    }

    public record ExportFilters(String search, String status, String service, Long assignedAdminId,
                                Instant startDate, Instant endDate, String sortBy, String sortDir) {}

    public record ExportResult(byte[] content, String filename, long rows) {}

    public ExportResult export(ExportFilters filters, String username) {
        AdminUser user = username == null ? null : adminUserRepository.findByUsername(username).orElse(null);
        ZoneId zone = TimeZones.zoneOrDefault(user != null ? user.getTimezone() : null);
        Instant generatedAt = Instant.now();
        var sort = EnquiryService.safeSort(filters.sortBy(), filters.sortDir());

        PagedResponse<EnquiryResponseDto> first = page(filters, 0, sort);
        long total = first.getTotalElements();
        if (total > MAX_EXPORT_ROWS) {
            throw new BadRequestException("This export has " + total + " enquiries; narrow the filters to at most "
                    + MAX_EXPORT_ROWS + " rows.");
        }

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
            Styles styles = new Styles(workbook);
            SXSSFSheet sheet = workbook.createSheet("Enquiries");
            for (int i = 0; i < WIDTHS.length; i++) {
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }

            writeReportHeader(sheet, styles, filters, zone, generatedAt, total, user);
            Row header = sheet.createRow(HEADER_ROW);
            header.setHeightInPoints(22);
            for (int i = 0; i < COLUMNS.length; i++) {
                String label = COLUMNS[i];
                if (i == 9 || i == 10) label += " (" + zone.getId() + ")";
                cell(header, i, label, styles.header);
            }

            int rowIndex = HEADER_ROW + 1;
            PagedResponse<EnquiryResponseDto> current = first;
            for (int p = 0; ; p++) {
                for (EnquiryResponseDto e : current.getContent()) {
                    writeEnquiry(sheet.createRow(rowIndex++), e, zone, styles);
                }
                if (current.isLast() || current.getContent().isEmpty()) break;
                current = page(filters, p + 1, sort);
            }

            int lastRow = Math.max(HEADER_ROW, rowIndex - 1);
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, lastRow, 0, COLUMNS.length - 1));
            sheet.createFreezePane(0, HEADER_ROW + 1);
            if (total == 0) {
                cell(sheet.createRow(HEADER_ROW + 1), 0, "No enquiries match the applied filters.", styles.note);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out); // closing the workbook (try-with-resources) removes its temporary files

            String filterText = describeFilters(filters, zone);
            auditLogService.logAction(username, "EXPORT_ENQUIRIES", "ENQUIRY", null,
                    "Exported " + total + " enquiries to Excel; filters: " + filterText);
            String filename = "jovens-buzz-enquiries-" + generatedAt.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".xlsx";
            return new ExportResult(out.toByteArray(), filename, total);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the Excel export", e);
        }
    }

    private PagedResponse<EnquiryResponseDto> page(ExportFilters f, int page, org.springframework.data.domain.Sort sort) {
        return enquiryService.getEnquiries(f.search(), f.status(), f.service(), f.assignedAdminId(),
                f.startDate(), f.endDate(), PageRequest.of(page, PAGE_SIZE, sort));
    }

    private void writeReportHeader(SXSSFSheet sheet, Styles styles, ExportFilters filters, ZoneId zone,
                                   Instant generatedAt, long total, AdminUser user) {
        Row title = sheet.createRow(0);
        title.setHeightInPoints(26);
        cell(title, 0, "Jovens Buzz — Enquiries Export", styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COLUMNS.length - 1));

        String[][] details = {
                {"Generated", TimeZones.formatWithZone(generatedAt, zone) + (user != null ? " · by " + user.getDisplayName() : "")},
                {"Time zone", TimeZones.label(zone, generatedAt) + " — all dates and times below are in this zone"},
                {"Filters", describeFilters(filters, zone)},
                {"Sort", describeSort(filters)},
                {"Total records", String.valueOf(total)}};
        for (int i = 0; i < details.length; i++) {
            Row row = sheet.createRow(i + 1);
            cell(row, 0, details[i][0], styles.label);
            cell(row, 1, details[i][1], styles.value);
            sheet.addMergedRegion(new CellRangeAddress(i + 1, i + 1, 1, COLUMNS.length - 1));
        }
    }

    private void writeEnquiry(Row row, EnquiryResponseDto e, ZoneId zone, Styles styles) {
        cell(row, 0, e.getPublicReference(), styles.text);
        cell(row, 1, e.getFullName(), styles.text);
        cell(row, 2, e.getEmail(), styles.text);
        cell(row, 3, e.getPhone() == null ? "" : InternationalPhoneValidator.formatInternational(e.getPhone()), styles.text);
        cell(row, 4, e.getCompanyName(), styles.text);
        cell(row, 5, e.getServiceInterest(), styles.text);
        cell(row, 6, e.getMessage(), styles.wrap);
        cell(row, 7, titleCase(e.getStatus()), styles.text);
        cell(row, 8, e.getAssignedAdminName() == null ? "Unassigned" : e.getAssignedAdminName(), styles.text);
        if (e.getCreatedAt() != null) {
            LocalDateTime local = LocalDateTime.ofInstant(e.getCreatedAt(), zone);
            Cell date = row.createCell(9);
            date.setCellValue(local.toLocalDate());
            date.setCellStyle(styles.date);
            Cell time = row.createCell(10);
            time.setCellValue(local.toLocalTime().toSecondOfDay() / 86_400d);
            time.setCellStyle(styles.time);
        }
        cell(row, 11, syncLabel(e.getSyncStatus()), styles.text);
    }

    String describeFilters(ExportFilters f, ZoneId zone) {
        List<String> parts = new ArrayList<>();
        if (notBlank(f.search())) parts.add("Search = \"" + f.search().trim() + "\"");
        if (notBlank(f.status()) && !"ALL".equalsIgnoreCase(f.status())) parts.add("Status = " + titleCase(f.status()));
        if (notBlank(f.service()) && !"ALL".equalsIgnoreCase(f.service())) parts.add("Service = " + f.service().trim());
        if (f.assignedAdminId() != null) {
            parts.add("Assigned to = " + adminUserRepository.findById(f.assignedAdminId())
                    .map(AdminUser::getDisplayName).orElse("#" + f.assignedAdminId()));
        }
        DateTimeFormatter day = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
        if (f.startDate() != null || f.endDate() != null) {
            String from = f.startDate() != null ? f.startDate().atZone(zone).format(day) : "the beginning";
            String to = f.endDate() != null ? f.endDate().atZone(zone).format(day) : "now";
            parts.add("Date range = " + from + " – " + to);
        }
        return parts.isEmpty() ? "None (all enquiries)" : String.join("; ", parts);
    }

    private static String describeSort(ExportFilters f) {
        String field = SORT_LABELS.getOrDefault(f.sortBy(), "Submitted date");
        boolean asc = "asc".equalsIgnoreCase(f.sortDir());
        if (!SORT_LABELS.containsKey(f.sortBy())) asc = false;
        return field + (asc ? ", ascending" : ", descending");
    }

    private static String syncLabel(String status) {
        if (status == null) return "Not queued";
        return switch (status) {
            case "SUCCESS" -> "Synced";
            case "FAILED" -> "Failed";
            case "RETRYING" -> "Retrying";
            case "PENDING", "PROCESSING" -> "Pending";
            default -> "Not queued";
        };
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value); // stored as text, never evaluated as a formula
        c.setCellStyle(style);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        String v = value.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    /** Workbook styles (created once per workbook). */
    private static final class Styles {
        final CellStyle title, label, value, header, text, wrap, date, time, note;

        Styles(Workbook wb) {
            DataFormat format = wb.createDataFormat();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 15);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font bold = wb.createFont();
            bold.setBold(true);
            label = wb.createCellStyle();
            label.setFont(bold);
            value = wb.createCellStyle();

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            borders(header);

            text = wb.createCellStyle();
            text.setVerticalAlignment(VerticalAlignment.TOP);
            borders(text);
            wrap = wb.createCellStyle();
            wrap.cloneStyleFrom(text);
            wrap.setWrapText(true);
            date = wb.createCellStyle();
            date.cloneStyleFrom(text);
            date.setDataFormat(format.getFormat("yyyy-mm-dd"));
            time = wb.createCellStyle();
            time.cloneStyleFrom(text);
            time.setDataFormat(format.getFormat("h:mm AM/PM"));

            Font italic = wb.createFont();
            italic.setItalic(true);
            note = wb.createCellStyle();
            note.setFont(italic);
        }

        private static void borders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            short grey = IndexedColors.GREY_40_PERCENT.getIndex();
            style.setTopBorderColor(grey);
            style.setBottomBorderColor(grey);
            style.setLeftBorderColor(grey);
            style.setRightBorderColor(grey);
        }
    }
}
