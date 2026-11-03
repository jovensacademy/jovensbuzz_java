package com.jovensbuzz.enquiry.service;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.util.ReferenceGenerator;
import com.jovensbuzz.common.util.TimeZones;
import com.jovensbuzz.common.validation.InputSanitizer;
import com.jovensbuzz.common.validation.InternationalPhoneValidator;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.enquiry.entity.EnquiryStatusHistory;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.enquiry.repository.EnquiryStatusHistoryRepository;
import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import com.jovensbuzz.integration.sync.service.SyncJobQueuedEvent;
import com.jovensbuzz.servicecatalog.entity.ServiceItem;
import com.jovensbuzz.servicecatalog.repository.ServiceItemRepository;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Excel import of enquiries in two steps:
 * 1. preview: parse an uploaded .xlsx/.xls, validate every row and flag duplicates — nothing is saved;
 * 2. commit: the (optionally corrected) rows are validated again and inserted in ONE transaction,
 *    so either every row is saved or none is.
 * Imported enquiries never send emails. They are added to Google Sheets only when requested.
 * "Submitted At" values are read in the importing user's time zone and stored in UTC.
 */
@Service
public class EnquiryExcelImportService {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_ROWS = 5_000;
    public static final String SOURCE = "ADMIN_IMPORT";
    private static final String SHEETS = "GOOGLE_SHEETS";
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm"), DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm"));
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("M/d/yyyy"));
    private static final Map<String, String> COUNTRY_NAMES = Map.of(
            "UNITED STATES", "US", "USA", "US", "INDIA", "IN", "CANADA", "CA",
            "UNITED KINGDOM", "GB", "UK", "GB", "AUSTRALIA", "AU");

    /** Template / preferred header order. */
    static final String[] TEMPLATE_HEADERS = {
            "First Name", "Last Name", "Email", "Country", "Phone", "Company", "Service", "Message", "Status", "Submitted At"};

    private final EnquiryRepository enquiryRepository;
    private final EnquiryStatusHistoryRepository statusHistoryRepository;
    private final IntegrationSyncJobRepository syncJobRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final AdminUserRepository adminUserRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public EnquiryExcelImportService(EnquiryRepository enquiryRepository, EnquiryStatusHistoryRepository statusHistoryRepository,
                                     IntegrationSyncJobRepository syncJobRepository, ServiceItemRepository serviceItemRepository,
                                     AdminUserRepository adminUserRepository, AuditLogService auditLogService,
                                     ApplicationEventPublisher eventPublisher) {
        this.enquiryRepository = enquiryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.syncJobRepository = syncJobRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.adminUserRepository = adminUserRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------- DTOs

    public record RowInput(Integer rowNumber, String firstName, String lastName, String email, String country, String phone,
                           String company, String service, String message, String status, String submittedAt) {}

    /** state: VALID, INVALID or DUPLICATE. */
    public record RowResult(int rowNumber, RowInput values, String state, List<String> errors, String duplicateOf) {}

    public record Preview(String filename, String timezone, int total, int valid, int invalid, int duplicates, List<RowResult> rows) {}

    public record CommitRequest(List<RowInput> rows, boolean addToGoogleSheets, String filename, Integer skipped) {}

    public record Summary(int imported, int skipped, boolean addedToGoogleSheets, String timezone, List<String> references) {}

    /** Thrown when a commit contains invalid or duplicate rows; nothing has been saved. */
    public static class ImportRejectedException extends RuntimeException {
        private final List<RowResult> rows;

        public ImportRejectedException(String message, List<RowResult> rows) {
            super(message);
            this.rows = rows;
        }

        public List<RowResult> getRows() {
            return rows;
        }
    }

    // ---------------------------------------------------------------- template

    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Enquiries");
            CellStyle header = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row h = sheet.createRow(0);
            int[] widths = {16, 16, 30, 10, 20, 24, 26, 48, 12, 20};
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(TEMPLATE_HEADERS[i]);
                c.setCellStyle(header);
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            String[] example = {"Jane", "Doe", "jane@example.com", "US", "(201) 555-0123", "Example LLC",
                    serviceTitles().get(0), "Interested in a monthly content plan.", "NEW", "2026-10-31 09:30"};
            Row r = sheet.createRow(1);
            for (int i = 0; i < example.length; i++) r.createCell(i).setCellValue(example[i]);
            sheet.createFreezePane(0, 1);

            DataValidationHelper dv = sheet.getDataValidationHelper();
            addList(sheet, dv, 3, InternationalPhoneValidator.SUPPORTED_COUNTRIES.toArray(String[]::new));
            addList(sheet, dv, 8, Arrays.stream(EnquiryStatus.values()).map(Enum::name).toArray(String[]::new));
            List<String> services = serviceTitles();
            String joined = String.join(",", services);
            if (joined.length() < 250) addList(sheet, dv, 6, services.toArray(String[]::new)); // Excel list limit

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "Jovens Buzz — enquiry import template",
                    "Required: First Name, Last Name, Email, Phone, Service. Country is required unless Phone starts with + and a country code.",
                    "Country: ISO code US, IN, CA, GB or AU (United States and Canada share +1, so choose the right one).",
                    "Service: one of — " + joined,
                    "Status (optional, default NEW): " + String.join(", ", Arrays.stream(EnquiryStatus.values()).map(Enum::name).toList()),
                    "Submitted At (optional, default: time of import): yyyy-MM-dd HH:mm or M/d/yyyy h:mm AM/PM, in YOUR dashboard time zone.",
                    "Duplicates (same email, service and message as an existing enquiry) are detected and can be skipped.",
                    "Imported enquiries do not send emails. Maximum " + MAX_ROWS + " rows and 5 MB per file."};
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 120 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the import template", e);
        }
    }

    private static void addList(Sheet sheet, DataValidationHelper dv, int col, String[] values) {
        DataValidation validation = dv.createValidation(dv.createExplicitListConstraint(values),
                new CellRangeAddressList(1, MAX_ROWS, col, col));
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    // ---------------------------------------------------------------- preview

    public Preview preview(MultipartFile file, String username) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose an Excel file (.xlsx or .xls) to import");
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("upload").replaceAll("[^A-Za-z0-9._ -]", "_");
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new BadRequestException("Unsupported file type. Upload an .xlsx or .xls file");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("The file is larger than 5 MB");
        }
        ZoneId zone = zoneOf(username);
        List<RowInput> inputs;
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            inputs = readRows(wb, zone);
        } catch (EncryptedDocumentException e) {
            throw new BadRequestException("Password-protected workbooks cannot be imported");
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bre) throw bre;
            throw new BadRequestException("This file could not be read as an Excel workbook");
        }
        if (inputs.isEmpty()) {
            throw new BadRequestException("The file has no enquiry rows below the header");
        }
        List<RowResult> results = validateAll(inputs, zone);
        return toPreview(name, zone, results);
    }

    /** Re-validates rows the user corrected in the browser (same rules as preview; saves nothing). */
    public Preview validateRows(List<RowInput> rows, String filename, String username) {
        if (rows == null || rows.isEmpty()) {
            throw new BadRequestException("There are no rows to validate");
        }
        if (rows.size() > MAX_ROWS) {
            throw new BadRequestException("At most " + MAX_ROWS + " rows can be validated at once");
        }
        ZoneId zone = zoneOf(username);
        return toPreview(filename == null ? "upload" : InputSanitizer.plainText(filename), zone, validateAll(rows, zone));
    }

    List<RowInput> readRows(Workbook wb, ZoneId zone) {
        Sheet sheet = Optional.ofNullable(wb.getSheet("Enquiries")).orElse(wb.getSheetAt(0));
        DataFormatter formatter = new DataFormatter(Locale.US);
        int headerRow = -1;
        Map<String, Integer> cols = new HashMap<>();
        for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), 10) && headerRow < 0; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> found = new HashMap<>();
            for (Cell c : row) {
                String key = headerKey(text(c, formatter, zone));
                if (key != null) found.putIfAbsent(key, c.getColumnIndex());
            }
            if (found.containsKey("email")) {
                headerRow = r;
                cols = found;
            }
        }
        if (headerRow < 0) {
            throw new BadRequestException("Header row not found. Use the template: the first row must contain the column names (Email, Phone, Service…)");
        }
        List<String> missing = new ArrayList<>();
        if (!cols.containsKey("phone")) missing.add("Phone");
        if (!cols.containsKey("service")) missing.add("Service");
        if (!(cols.containsKey("firstName") && cols.containsKey("lastName")) && !cols.containsKey("name")) missing.add("First Name and Last Name");
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing required column(s): " + String.join(", ", missing));
        }

        List<RowInput> rows = new ArrayList<>();
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, String> v = new HashMap<>();
            boolean blank = true;
            for (Map.Entry<String, Integer> col : cols.entrySet()) {
                String value = text(row.getCell(col.getValue()), formatter, zone).trim();
                v.put(col.getKey(), value);
                if (!value.isEmpty()) blank = false;
            }
            if (blank) continue;
            if (rows.size() >= MAX_ROWS) {
                throw new BadRequestException("The file has more than " + MAX_ROWS + " rows; split it into smaller files");
            }
            String first = v.getOrDefault("firstName", "");
            String last = v.getOrDefault("lastName", "");
            if (first.isEmpty() && last.isEmpty() && !v.getOrDefault("name", "").isEmpty()) {
                String full = v.get("name");
                int space = full.indexOf(' ');
                first = space < 0 ? full : full.substring(0, space);
                last = space < 0 ? "" : full.substring(space + 1).trim();
            }
            String submitted = v.getOrDefault("submittedAt", "");
            if (!submitted.isEmpty() && !v.getOrDefault("submittedTime", "").isEmpty() && submitted.length() <= 10) {
                submitted = submitted + " " + v.get("submittedTime");
            }
            rows.add(new RowInput(r + 1, first, last, v.get("email"), v.getOrDefault("country", ""), v.getOrDefault("phone", ""),
                    v.getOrDefault("company", ""), v.getOrDefault("service", ""), v.getOrDefault("message", ""),
                    v.getOrDefault("status", ""), submitted));
        }
        return rows;
    }

    /** Maps a header label (template or our own export) to a field key. */
    static String headerKey(String raw) {
        if (raw == null) return null;
        String h = raw.toLowerCase(Locale.ROOT).replace("*", "").replaceAll("\\(.*\\)", "").replaceAll("\\s+", " ").trim();
        return switch (h) {
            case "first name", "firstname" -> "firstName";
            case "last name", "lastname", "surname" -> "lastName";
            case "name", "full name" -> "name";
            case "email", "email address", "company email", "e-mail" -> "email";
            case "country", "phone country", "country iso" -> "country";
            case "phone", "phone number", "mobile" -> "phone";
            case "company", "company / organization", "company/organization", "organization", "organisation" -> "company";
            case "service", "service interest", "how can we help you?", "how can we help you" -> "service";
            case "message" -> "message";
            case "status" -> "status";
            case "submitted at", "submitted date", "created date", "created at", "date" -> "submittedAt";
            case "submitted time", "time" -> "submittedTime";
            default -> null;
        };
    }

    /** Cell as text; date cells become "yyyy-MM-dd HH:mm", formulas use their cached value (never evaluated). */
    private static String text(Cell cell, DataFormatter formatter, ZoneId zone) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dt = cell.getLocalDateTimeCellValue();
                    double v = cell.getNumericCellValue();
                    if (v < 1) yield dt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")); // time-only cell
                    yield dt.format(OUT);
                }
                yield cell.getCellType() == CellType.FORMULA ? String.valueOf(cell.getNumericCellValue()) : formatter.formatCellValue(cell);
            }
            default -> "";
        };
    }

    // ---------------------------------------------------------------- commit

    @Transactional
    public Summary commit(CommitRequest request, String username) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw new BadRequestException("There are no rows to import");
        }
        if (request.rows().size() > MAX_ROWS) {
            throw new BadRequestException("At most " + MAX_ROWS + " rows can be imported at once");
        }
        ZoneId zone = zoneOf(username);
        List<RowResult> results = validateAll(request.rows(), zone);
        List<RowResult> problems = results.stream().filter(r -> !"VALID".equals(r.state())).toList();
        if (!problems.isEmpty()) {
            throw new ImportRejectedException(problems.size() + " row(s) still have errors or are duplicates. Fix or skip them; nothing was imported.", problems);
        }

        String actor = username != null ? username : "ADMIN";
        List<String> references = new ArrayList<>();
        Set<String> usedReferences = new HashSet<>();
        Map<String, String> services = serviceLookup();
        Instant now = Instant.now();
        for (RowInput in : request.rows()) {
            Normalised n = normalise(in, zone, now, services).normalised();
            Enquiry e = new Enquiry();
            e.setPublicReference(uniqueReference(usedReferences));
            e.setFirstName(n.firstName);
            e.setLastName(n.lastName);
            e.setFullName((n.firstName + " " + n.lastName).trim());
            e.setEmail(n.email);
            e.setPhone(n.e164);
            e.setPhoneCountry(n.country);
            e.setCompanyName(n.company);
            e.setServiceInterest(n.service);
            e.setMessage(n.message);
            e.setStatus(n.status);
            e.setSource(SOURCE);
            e.setPrivacyConsent(false);
            e.setSubmissionFingerprint(n.fingerprint);
            e.setCreatedAt(n.createdAt);
            Enquiry saved = enquiryRepository.save(e);
            statusHistoryRepository.save(new EnquiryStatusHistory(saved, actor, null, n.status.name()));
            if (request.addToGoogleSheets()) {
                IntegrationSyncJob job = syncJobRepository.save(new IntegrationSyncJob(saved, SHEETS));
                eventPublisher.publishEvent(new SyncJobQueuedEvent(job.getId())); // processed after commit
            }
            references.add(saved.getPublicReference());
        }
        int skipped = request.skipped() != null ? Math.max(0, request.skipped()) : 0;
        String file = request.filename() != null ? InputSanitizer.plainText(request.filename()) : "upload";
        auditLogService.logAction(username, "IMPORT_ENQUIRIES", "ENQUIRY", null,
                "Imported " + references.size() + " enquiries from '" + file + "'; skipped " + skipped
                        + (request.addToGoogleSheets() ? "; queued for Google Sheets" : "; not sent to Google Sheets")
                        + "; time zone " + zone.getId());
        return new Summary(references.size(), skipped, request.addToGoogleSheets(), zone.getId(), references);
    }

    private String uniqueReference(Set<String> used) {
        for (int i = 0; i < 10; i++) {
            String candidate = ReferenceGenerator.generateEnquiryReference();
            if (used.add(candidate) && !enquiryRepository.existsByPublicReference(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not allocate a unique enquiry reference");
    }

    // ---------------------------------------------------------------- validation

    private record Normalised(String firstName, String lastName, String email, String e164, String country, String company,
                              String service, String message, EnquiryStatus status, Instant createdAt, String fingerprint) {}

    private record Checked(Normalised normalised, List<String> errors) {}

    List<RowResult> validateAll(List<RowInput> inputs, ZoneId zone) {
        Instant now = Instant.now();
        Map<String, String> serviceLookup = serviceLookup();
        List<Checked> checked = new ArrayList<>();
        for (RowInput in : inputs) checked.add(normalise(in, zone, now, serviceLookup));

        Set<String> fingerprints = new HashSet<>();
        checked.forEach(c -> { if (c.normalised() != null) fingerprints.add(c.normalised().fingerprint()); });
        Map<String, String> existing = new HashMap<>();
        List<String> all = new ArrayList<>(fingerprints);
        for (int i = 0; i < all.size(); i += 1000) {
            for (Object[] pair : enquiryRepository.findReferencesByFingerprints(all.subList(i, Math.min(all.size(), i + 1000)))) {
                existing.putIfAbsent((String) pair[0], (String) pair[1]);
            }
        }

        Map<String, Integer> firstSeen = new HashMap<>();
        List<RowResult> results = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            RowInput in = inputs.get(i);
            int rowNumber = in.rowNumber() != null ? in.rowNumber() : i + 2;
            Checked c = checked.get(i);
            if (!c.errors().isEmpty()) {
                results.add(new RowResult(rowNumber, in, "INVALID", c.errors(), null));
                continue;
            }
            String fp = c.normalised().fingerprint();
            if (existing.containsKey(fp)) {
                results.add(new RowResult(rowNumber, in, "DUPLICATE",
                        List.of("Already exists as enquiry " + existing.get(fp) + " (same email, service and message)"), existing.get(fp)));
            } else if (firstSeen.containsKey(fp)) {
                results.add(new RowResult(rowNumber, in, "DUPLICATE",
                        List.of("Same as row " + firstSeen.get(fp) + " in this file"), "row " + firstSeen.get(fp)));
            } else {
                firstSeen.put(fp, rowNumber);
                results.add(new RowResult(rowNumber, in, "VALID", List.of(), null));
            }
        }
        return results;
    }

    private Checked normalise(RowInput in, ZoneId zone, Instant now, Map<String, String> serviceLookup) {
        List<String> errors = new ArrayList<>();
        String first = clean(in.firstName());
        String last = clean(in.lastName());
        if (first.isEmpty()) errors.add("First name is required");
        if (last.isEmpty()) errors.add("Last name is required");
        if (first.length() > 60 || last.length() > 60) errors.add("First and last name must be at most 60 characters each");
        if (!first.isEmpty() && (first + " " + last).trim().length() < 2) errors.add("Name must be at least 2 characters");

        String email = clean(in.email()).toLowerCase(Locale.ROOT);
        if (email.isEmpty()) errors.add("Email is required");
        else if (email.length() > 128 || !EMAIL.matcher(email).matches()) errors.add("Invalid email address: " + email);

        String country = resolveCountry(clean(in.country()));
        String e164 = null;
        String phoneCountry = null;
        String phone = clean(in.phone());
        if (country == null && !clean(in.country()).isEmpty()) {
            errors.add("Country must be one of " + InternationalPhoneValidator.SUPPORTED_COUNTRIES);
        } else if (phone.isEmpty()) {
            errors.add("Phone is required");
        } else if (country == null && !phone.startsWith("+")) {
            errors.add("Country is required when the phone number has no + country code");
        } else {
            try {
                InternationalPhoneValidator.NormalizedPhone normalized = InternationalPhoneValidator.normalize(phone, country);
                e164 = normalized.e164();
                phoneCountry = normalized.country();
            } catch (BadRequestException ex) {
                errors.add("Invalid phone number" + (country != null ? " for " + country : "") + ": " + phone);
            }
        }

        String company = clean(in.company());
        if (company.length() > 128) errors.add("Company must be at most 128 characters");
        String service = null;
        String requestedService = clean(in.service());
        if (requestedService.isEmpty()) errors.add("Service is required");
        else {
            service = serviceLookup.get(requestedService.toLowerCase(Locale.ROOT));
            if (service == null) errors.add("Unknown service \"" + requestedService + "\". Use one of: " + String.join(", ", serviceLookup.values()));
        }

        String message = InputSanitizer.multiLineText(in.message());
        if (message != null && message.length() > 4000) errors.add("Message must be at most 4000 characters");

        EnquiryStatus status = EnquiryStatus.NEW;
        String requestedStatus = clean(in.status());
        if (!requestedStatus.isEmpty()) {
            try {
                status = EnquiryStatus.valueOf(requestedStatus.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                errors.add("Unknown status \"" + requestedStatus + "\". Use one of: " + Arrays.toString(EnquiryStatus.values()));
            }
        }

        Instant createdAt = now;
        String submitted = clean(in.submittedAt());
        if (!submitted.isEmpty()) {
            LocalDateTime local = parseDateTime(submitted);
            if (local == null) {
                errors.add("Invalid Submitted At \"" + submitted + "\". Use yyyy-MM-dd HH:mm or M/d/yyyy h:mm AM/PM");
            } else {
                createdAt = local.atZone(zone).toInstant(); // DST-aware conversion to UTC
                if (createdAt.isAfter(now.plusSeconds(60))) errors.add("Submitted At cannot be in the future");
                if (local.getYear() < 2000) errors.add("Submitted At is before the year 2000");
            }
        }

        if (!errors.isEmpty()) return new Checked(null, errors);
        String fingerprint = EnquiryService.fingerprint(email, service, message == null ? "" : message);
        return new Checked(new Normalised(InputSanitizer.plainText(first), InputSanitizer.plainText(last), email, e164, phoneCountry,
                company.isEmpty() ? null : InputSanitizer.plainText(company), service, message, status, createdAt, fingerprint), List.of());
    }

    static LocalDateTime parseDateTime(String value) {
        for (DateTimeFormatter f : DATE_TIMES) {
            try {
                return LocalDateTime.parse(value, f);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(value, f).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        return null;
    }

    private static String resolveCountry(String value) {
        if (value.isEmpty()) return null;
        String upper = value.toUpperCase(Locale.ROOT);
        if (InternationalPhoneValidator.SUPPORTED_COUNTRIES.contains(upper)) return upper;
        return COUNTRY_NAMES.get(upper);
    }

    /** lower-case title -> canonical title, for every service (published or not) plus the generic choices. */
    private Map<String, String> serviceLookup() {
        Map<String, String> lookup = new LinkedHashMap<>();
        for (String title : serviceTitles()) lookup.putIfAbsent(title.toLowerCase(Locale.ROOT), title);
        return lookup;
    }

    private List<String> serviceTitles() {
        List<String> titles = new ArrayList<>(serviceItemRepository.findAllByOrderByDisplayOrderAsc().stream().map(ServiceItem::getTitle).toList());
        titles.addAll(EnquiryService.GENERIC_SERVICE_OPTIONS);
        return titles;
    }

    private ZoneId zoneOf(String username) {
        AdminUser user = username == null ? null : adminUserRepository.findByUsername(username).orElse(null);
        return TimeZones.zoneOrDefault(user != null ? user.getTimezone() : null);
    }

    private static Preview toPreview(String filename, ZoneId zone, List<RowResult> results) {
        int valid = (int) results.stream().filter(r -> r.state().equals("VALID")).count();
        int duplicates = (int) results.stream().filter(r -> r.state().equals("DUPLICATE")).count();
        return new Preview(filename, zone.getId(), results.size(), valid, results.size() - valid - duplicates, duplicates, results);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
