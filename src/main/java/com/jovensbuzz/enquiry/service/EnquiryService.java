package com.jovensbuzz.enquiry.service;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.exception.ResourceNotFoundException;
import com.jovensbuzz.common.response.PagedResponse;
import com.jovensbuzz.common.util.ReferenceGenerator;
import com.jovensbuzz.common.validation.InputSanitizer;
import com.jovensbuzz.common.validation.InternationalPhoneValidator;
import com.jovensbuzz.integration.email.service.EmailNotificationService;
import com.jovensbuzz.enquiry.dto.*;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryNote;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.enquiry.entity.EnquiryStatusHistory;
import com.jovensbuzz.enquiry.repository.EnquiryNoteRepository;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.enquiry.repository.EnquiryStatusHistoryRepository;
import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;
import com.jovensbuzz.integration.sync.entity.SyncStatus;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import com.jovensbuzz.integration.sync.service.SyncJobProcessor;
import com.jovensbuzz.integration.sync.service.SyncJobQueuedEvent;
import com.jovensbuzz.servicecatalog.entity.ServiceItem;
import com.jovensbuzz.servicecatalog.repository.ServiceItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EnquiryService {

    private static final Logger log = LoggerFactory.getLogger(EnquiryService.class);
    private static final String SHEETS = "GOOGLE_SHEETS";
    /** Identical submissions inside this window are treated as one (double-clicks, retries). */
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(15);
    private static final Set<String> ALLOWED_SOURCES = Set.of("WEBSITE_LANDING", "WEBSITE_CONTACT");
    /** Generic choices offered by the contact form in addition to the published services. */
    public static final List<String> GENERIC_SERVICE_OPTIONS = List.of("Not sure yet", "Other");
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "fullName", "email", "companyName", "serviceInterest", "status", "publicReference");
    private static final int MAX_EXPORT_ROWS = 10_000;

    private final EnquiryRepository enquiryRepository;
    private final EnquiryNoteRepository enquiryNoteRepository;
    private final EnquiryStatusHistoryRepository statusHistoryRepository;
    private final IntegrationSyncJobRepository syncJobRepository;
    private final AdminUserRepository adminUserRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final SyncJobProcessor syncJobProcessor;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailNotificationService emailNotificationService;

    public EnquiryService(
            EnquiryRepository enquiryRepository,
            EnquiryNoteRepository enquiryNoteRepository,
            EnquiryStatusHistoryRepository statusHistoryRepository,
            IntegrationSyncJobRepository syncJobRepository,
            AdminUserRepository adminUserRepository,
            ServiceItemRepository serviceItemRepository,
            SyncJobProcessor syncJobProcessor,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher,
            EmailNotificationService emailNotificationService) {
        this.enquiryRepository = enquiryRepository;
        this.enquiryNoteRepository = enquiryNoteRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.syncJobRepository = syncJobRepository;
        this.adminUserRepository = adminUserRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.syncJobProcessor = syncJobProcessor;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.emailNotificationService = emailNotificationService;
    }

    // ---------------------------------------------------------------- public submission

    /**
     * Stores the enquiry, its initial history, its Google Sheets job and its email notification
     * jobs in one transaction. Sheets sync and emails start only after commit, so an outage of
     * either can never lose an enquiry or delay the visitor.
     */
    @Transactional
    public EnquirySubmissionResponse createEnquiry(EnquiryCreateRequest request) {
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            log.warn("Rejected enquiry: honeypot field was filled");
            throw new BadRequestException("Your enquiry could not be submitted. Please try again.");
        }

        String fullName = InputSanitizer.plainText(request.getFullName());
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        InternationalPhoneValidator.NormalizedPhone phone =
                InternationalPhoneValidator.normalize(request.getPhone(), request.getPhoneCountry());
        String company = InputSanitizer.plainText(request.getCompanyName());
        String service = request.getServiceId() != null
                ? resolveServiceById(request.getServiceId())
                : resolveServiceInterest(request.getServiceInterest());
        String budget = InputSanitizer.plainText(request.getBudget());
        String message = InputSanitizer.multiLineText(request.getMessage());
        if (fullName == null || fullName.length() < 2) {
            throw new BadRequestException("Please provide your full name");
        }
        if (message == null || message.length() < 10) {
            throw new BadRequestException("Please tell us a little about your project");
        }

        String fingerprint = fingerprint(email, service, message);
        Optional<Enquiry> recentDuplicate = enquiryRepository
                .findFirstBySubmissionFingerprintAndCreatedAtAfterOrderByCreatedAtDesc(
                        fingerprint, Instant.now().minus(DUPLICATE_WINDOW));
        if (recentDuplicate.isPresent()) {
            Enquiry existing = recentDuplicate.get();
            log.info("Duplicate submission collapsed into existing enquiry '{}'", existing.getPublicReference());
            return new EnquirySubmissionResponse(existing.getPublicReference(), existing.getCreatedAt(), true);
        }

        Enquiry enquiry = new Enquiry();
        enquiry.setPublicReference(uniqueReference());
        enquiry.setFullName(fullName);
        enquiry.setFirstName(InputSanitizer.plainText(request.getFirstName()));
        enquiry.setLastName(InputSanitizer.plainText(request.getLastName()));
        enquiry.setEmail(email);
        enquiry.setPhone(phone.e164());
        enquiry.setPhoneCountry(phone.country());
        enquiry.setCompanyName(company);
        enquiry.setServiceInterest(service);
        enquiry.setBudget(budget);
        enquiry.setMessage(message);
        enquiry.setPrivacyConsent(request.isPrivacyConsent());
        String policyVersion = request.getPrivacyPolicyVersion() != null && !request.getPrivacyPolicyVersion().isBlank()
                ? request.getPrivacyPolicyVersion().trim()
                : "2026-10";
        enquiry.setPrivacyPolicyVersion(policyVersion);
        enquiry.setStatus(EnquiryStatus.NEW);
        enquiry.setSource(ALLOWED_SOURCES.contains(request.getSource()) ? request.getSource() : "WEBSITE_LANDING");
        enquiry.setSubmissionFingerprint(fingerprint);

        Enquiry savedEnquiry = enquiryRepository.save(enquiry);
        statusHistoryRepository.save(new EnquiryStatusHistory(savedEnquiry, "SYSTEM", null, EnquiryStatus.NEW.name()));
        IntegrationSyncJob savedSyncJob = syncJobRepository.save(new IntegrationSyncJob(savedEnquiry, SHEETS));

        log.info("Created enquiry '{}' (id {}), queued sync job {}",
                savedEnquiry.getPublicReference(), savedEnquiry.getId(), savedSyncJob.getId());
        eventPublisher.publishEvent(new SyncJobQueuedEvent(savedSyncJob.getId()));
        emailNotificationService.queueForNewEnquiry(savedEnquiry);

        return new EnquirySubmissionResponse(savedEnquiry.getPublicReference(), savedEnquiry.getCreatedAt(), false);
    }

    /**
     * The requested service must be a published service or one of the generic options,
     * so dashboard grouping stays clean and arbitrary strings cannot be injected.
     */
    private String resolveServiceById(Long serviceId) {
        return serviceItemRepository.findById(serviceId)
                .filter(ServiceItem::isPublished)
                .map(ServiceItem::getTitle)
                .orElseThrow(() -> new BadRequestException("Please choose a service from the list"));
    }

    private String resolveServiceInterest(String requested) {
        String value = InputSanitizer.plainText(requested);
        if (value == null) {
            throw new BadRequestException("Service of interest is required");
        }
        for (String generic : GENERIC_SERVICE_OPTIONS) {
            if (generic.equalsIgnoreCase(value)) return generic;
        }
        return serviceItemRepository.findByPublishedTrueOrderByDisplayOrderAsc().stream()
                .map(ServiceItem::getTitle)
                .filter(title -> title.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Please choose a service from the list"));
    }

    private String uniqueReference() {
        for (int i = 0; i < 5; i++) {
            String candidate = ReferenceGenerator.generateEnquiryReference();
            if (!enquiryRepository.existsByPublicReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique enquiry reference");
    }

    static String fingerprint(String email, String service, String message) {
        String normalised = (email + "|" + service + "|" + message.replaceAll("\\s+", " ").trim())
                .toLowerCase(Locale.ROOT);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalised.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- admin queries

    public static Sort safeSort(String sortBy, String sortDir) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, field);
        return field.equals("createdAt") ? sort : sort.and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Transactional(readOnly = true)
    public PagedResponse<EnquiryResponseDto> getEnquiries(
            String search,
            String statusStr,
            String service,
            Long assignedAdminId,
            Instant startDate,
            Instant endDate,
            Pageable pageable) {

        EnquiryStatus status = parseStatusFilter(statusStr);
        String searchParam = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        if (searchParam != null && searchParam.length() > 100) {
            searchParam = searchParam.substring(0, 100);
        }
        String serviceParam = (service != null && !service.trim().isEmpty() && !service.equalsIgnoreCase("ALL"))
                ? service.trim() : null;

        Page<Enquiry> page = enquiryRepository.searchAndFilter(
                searchParam, status, serviceParam, assignedAdminId, startDate, endDate, pageable);

        Map<Long, String> syncStatuses = latestSyncStatuses(page.getContent());
        List<EnquiryResponseDto> dtos = page.getContent().stream()
                .map(enquiry -> EnquiryResponseDto.fromEntity(enquiry, syncStatuses.getOrDefault(enquiry.getId(), "NOT_QUEUED")))
                .collect(Collectors.toList());

        return new PagedResponse<>(
                dtos,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    private static EnquiryStatus parseStatusFilter(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return EnquiryStatus.valueOf(statusStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status filter: " + statusStr);
        }
    }

    /**
     * Batched lookup of the newest sync job per enquiry (one query instead of one per row).
     */
    public Map<Long, String> latestSyncStatuses(Collection<Enquiry> enquiries) {
        if (enquiries.isEmpty()) return Collections.emptyMap();
        List<Long> ids = enquiries.stream().map(Enquiry::getId).toList();
        return syncJobRepository.findByEnquiryIdsAndType(ids, SHEETS).stream()
                .collect(Collectors.toMap(
                        job -> job.getEnquiry().getId(),
                        Function.identity(),
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatus().name()));
    }

    @Transactional(readOnly = true)
    public EnquiryDetailResponseDto getEnquiryById(Long id) {
        Enquiry enquiry = findEnquiry(id);
        Optional<IntegrationSyncJob> syncJobOpt =
                syncJobRepository.findTopByEnquiryIdAndIntegrationTypeOrderByCreatedAtDesc(id, SHEETS);
        List<EnquiryNoteResponseDto> notes = getNotes(id);
        List<EnquiryStatusHistoryDto> history = statusHistoryRepository.findByEnquiryIdOrderByChangedAtDesc(id)
                .stream().map(EnquiryStatusHistoryDto::fromEntity).collect(Collectors.toList());
        EnquiryDetailResponseDto dto = EnquiryDetailResponseDto.fromEntity(enquiry, syncJobOpt.orElse(null), notes, history);
        if (syncJobOpt.isEmpty()) {
            dto.setSyncStatus("NOT_QUEUED");
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<EnquiryNoteResponseDto> getNotes(Long id) {
        if (!enquiryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Enquiry", "id", id);
        }
        return enquiryNoteRepository.findByEnquiryIdOrderByCreatedAtDesc(id)
                .stream().map(EnquiryNoteResponseDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SyncStatusDto getSyncStatus(Long id) {
        if (!enquiryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Enquiry", "id", id);
        }
        return syncJobRepository.findTopByEnquiryIdAndIntegrationTypeOrderByCreatedAtDesc(id, SHEETS)
                .map(SyncStatusDto::fromEntity)
                .orElseGet(SyncStatusDto::none);
    }

    @Transactional(readOnly = true)
    public List<String> getServiceFilterOptions() {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        serviceItemRepository.findAllByOrderByDisplayOrderAsc().forEach(s -> options.add(s.getTitle()));
        options.addAll(GENERIC_SERVICE_OPTIONS);
        options.addAll(enquiryRepository.findDistinctServiceInterests());
        return new ArrayList<>(options);
    }

    // ---------------------------------------------------------------- admin mutations

    @Transactional
    public EnquiryDetailResponseDto updateStatus(Long id, EnquiryStatusUpdateRequest request, String username) {
        Enquiry enquiry = findEnquiry(id);

        EnquiryStatus newStatus;
        try {
            newStatus = EnquiryStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + request.getStatus());
        }

        EnquiryStatus previous = enquiry.getStatus();
        if (previous == newStatus) {
            throw new BadRequestException("Enquiry is already " + newStatus.name());
        }
        if (!previous.canTransitionTo(newStatus)) {
            throw new BadRequestException("Cannot change status from " + previous.name() + " to " + newStatus.name()
                    + ". Allowed: " + previous.allowedTransitions());
        }

        enquiry.setStatus(newStatus);
        enquiryRepository.save(enquiry);
        statusHistoryRepository.save(new EnquiryStatusHistory(
                enquiry, username != null ? username : "ADMIN", previous.name(), newStatus.name()));

        String note = InputSanitizer.multiLineText(request.getNote());
        if (note != null) {
            AdminUser admin = username != null ? adminUserRepository.findByUsername(username).orElse(null) : null;
            enquiryNoteRepository.save(new EnquiryNote(enquiry, admin, "Status changed to " + newStatus.name() + ": " + note));
        }

        auditLogService.logAction(username, "UPDATE_STATUS", "ENQUIRY", String.valueOf(id),
                "Changed status from " + previous.name() + " to " + newStatus.name());

        return getEnquiryById(id);
    }

    @Transactional
    public EnquiryNoteResponseDto addNote(Long id, EnquiryNoteRequest request, String username) {
        Enquiry enquiry = findEnquiry(id);
        String text = InputSanitizer.multiLineText(request.getNote());
        if (text == null) {
            throw new BadRequestException("Note content cannot be empty");
        }
        AdminUser admin = username != null ? adminUserRepository.findByUsername(username).orElse(null) : null;
        EnquiryNote savedNote = enquiryNoteRepository.save(new EnquiryNote(enquiry, admin, text));

        auditLogService.logAction(username, "ADD_NOTE", "ENQUIRY", String.valueOf(id), "Added internal note");
        return EnquiryNoteResponseDto.fromEntity(savedNote);
    }

    @Transactional
    public EnquiryDetailResponseDto assign(Long id, EnquiryAssignmentRequest request, String username) {
        Enquiry enquiry = findEnquiry(id);
        AdminUser assignee = null;
        if (request.getAdminId() != null) {
            assignee = adminUserRepository.findById(request.getAdminId())
                    .filter(AdminUser::isEnabled)
                    .orElseThrow(() -> new BadRequestException("Selected administrator does not exist or is disabled"));
        }
        enquiry.setAssignedAdmin(assignee);
        enquiryRepository.save(enquiry);
        auditLogService.logAction(username, assignee != null ? "ASSIGN_ENQUIRY" : "UNASSIGN_ENQUIRY", "ENQUIRY",
                String.valueOf(id), assignee != null ? "Assigned to admin #" + assignee.getId() : "Unassigned");
        return getEnquiryById(id);
    }

    /**
     * Re-queues the enquiry's sync job and attempts it immediately. Safe to call repeatedly:
     * the worker claims jobs atomically and skips references already present in the sheet.
     * Intentionally not @Transactional: the Google API call must not hold a database transaction.
     */
    public boolean retrySync(Long id, String username) {
        Enquiry enquiry = findEnquiry(id);
        IntegrationSyncJob job = syncJobRepository.findTopByEnquiryIdAndIntegrationTypeOrderByCreatedAtDesc(id, SHEETS)
                .orElseGet(() -> syncJobRepository.save(new IntegrationSyncJob(enquiry, SHEETS)));

        if (job.getStatus() == SyncStatus.SUCCESS) {
            return true;
        }
        if (job.getStatus() == SyncStatus.PROCESSING) {
            throw new BadRequestException("Synchronisation is already in progress for this enquiry");
        }
        job.setStatus(SyncStatus.PENDING);
        job.setAttemptCount(0);
        job.setNextRetryAt(null);
        syncJobRepository.saveAndFlush(job);

        auditLogService.logAction(username, "RETRY_SYNC", "ENQUIRY", String.valueOf(id),
                "Manually triggered Google Sheets synchronization");

        return syncJobProcessor.processSingleJob(job.getId());
    }

    @Transactional
    public byte[] exportToCsv(String search, String statusStr, String service, Long assignedAdminId,
                              Instant startDate, Instant endDate, String username) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, MAX_EXPORT_ROWS, safeSort("createdAt", "desc"));
        PagedResponse<EnquiryResponseDto> response = getEnquiries(
                search, statusStr, service, assignedAdminId, startDate, endDate, pageable);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // UTF-8 BOM for Excel compatibility
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.print("Enquiry ID,Reference,Full Name,Email,Phone,Company,Service Interest,Budget,Message,Status,Assigned To,Source,Created At (UTC),Sync Status\r\n");
            for (EnquiryResponseDto e : response.getContent()) {
                writer.print(String.join(",",
                        csv(String.valueOf(e.getId())),
                        csv(e.getPublicReference()),
                        csv(e.getFullName()),
                        csv(e.getEmail()),
                        csv(e.getPhone()),
                        csv(e.getCompanyName()),
                        csv(e.getServiceInterest()),
                        csv(e.getBudget()),
                        csv(e.getMessage()),
                        csv(e.getStatus()),
                        csv(e.getAssignedAdminName()),
                        csv(e.getSource()),
                        csv(e.getCreatedAt() != null ? e.getCreatedAt().toString() : ""),
                        csv(e.getSyncStatus())) + "\r\n");
            }
        }
        auditLogService.logAction(username, "EXPORT_ENQUIRIES", "ENQUIRY", null,
                "Exported " + response.getContent().size() + " enquiries to CSV");
        return out.toByteArray();
    }

    /**
     * Quotes a CSV cell and neutralises spreadsheet formula prefixes (CSV injection).
     */
    static String csv(String value) {
        String safe = InputSanitizer.spreadsheetSafe(value == null ? "" : value);
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private Enquiry findEnquiry(Long id) {
        return enquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry", "id", id));
    }
}
