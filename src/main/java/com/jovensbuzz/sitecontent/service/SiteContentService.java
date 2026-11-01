package com.jovensbuzz.sitecontent.service;

import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.validation.InputSanitizer;
import com.jovensbuzz.sitecontent.dto.SiteContentDto;
import com.jovensbuzz.sitecontent.entity.SiteContent;
import com.jovensbuzz.sitecontent.repository.SiteContentRepository;
import com.jovensbuzz.sitecontent.service.ContentKeyRegistry.ContentKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SiteContentService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()\\-.]{6,40}$");

    private final SiteContentRepository siteContentRepository;
    private final AuditLogService auditLogService;

    public SiteContentService(
            SiteContentRepository siteContentRepository,
            AuditLogService auditLogService) {
        this.siteContentRepository = siteContentRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Only registered keys are ever served publicly.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getPublicContentMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (SiteContent item : siteContentRepository.findAll()) {
            if (ContentKeyRegistry.isPublic(item.getContentKey())) {
                map.put(item.getContentKey(), item.getContentValue());
            }
        }
        return map;
    }

    /**
     * Every registered key with its current value and editing metadata (missing keys appear empty).
     */
    @Transactional(readOnly = true)
    public List<SiteContentDto> getAllContent() {
        Map<String, SiteContent> stored = siteContentRepository.findAll().stream()
                .collect(Collectors.toMap(SiteContent::getContentKey, Function.identity()));
        List<SiteContentDto> result = new ArrayList<>();
        for (ContentKey key : ContentKeyRegistry.all()) {
            SiteContent item = stored.get(key.key());
            SiteContentDto dto = item != null ? SiteContentDto.fromEntity(item) : new SiteContentDto();
            dto.setContentKey(key.key());
            if (dto.getContentValue() == null) dto.setContentValue("");
            dto.setContentType(key.type().name());
            dto.setLabel(key.label());
            dto.setGroup(key.group());
            dto.setMaxLength(key.maxLength());
            dto.setRequired(key.required());
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public SiteContentDto updateContent(String contentKey, String contentValue, String username) {
        ContentKey key = ContentKeyRegistry.find(contentKey)
                .orElseThrow(() -> new BadRequestException("Unknown content key: " + contentKey));
        String value = validate(key, contentValue);

        SiteContent item = siteContentRepository.findByContentKey(key.key())
                .orElseGet(() -> new SiteContent(key.key(), value, key.type().name(), username));
        String previous = item.getContentValue();
        item.setContentValue(value);
        item.setContentType(key.type().name());
        item.setUpdatedBy(username);
        SiteContent saved = siteContentRepository.save(item);

        if (!Objects.equals(previous, value) || item.getId() == null) {
            auditLogService.logAction(username, "UPDATE_CONTENT", "SITE_CONTENT", key.key(), "Updated key: " + key.key());
        }
        return SiteContentDto.fromEntity(saved);
    }

    /**
     * Validates every entry before writing any, so a batch is applied all-or-nothing.
     */
    @Transactional
    public void batchUpdate(Map<String, String> updates, String username) {
        if (updates == null || updates.isEmpty()) {
            throw new BadRequestException("No content changes supplied");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            Optional<ContentKey> key = ContentKeyRegistry.find(entry.getKey());
            if (key.isEmpty()) {
                errors.put(entry.getKey(), "Unknown content key");
                continue;
            }
            try {
                validate(key.get(), entry.getValue());
            } catch (BadRequestException ex) {
                errors.put(entry.getKey(), ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Some content values are invalid: " + errors);
        }
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            updateContent(entry.getKey(), entry.getValue(), username);
        }
    }

    static String validate(ContentKey key, String raw) {
        String value = switch (key.type()) {
            case LONG_TEXT -> InputSanitizer.multiLineText(raw);
            case TEXT, PHONE -> InputSanitizer.plainText(raw);
            case EMAIL -> raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
            case URL -> {
                String url = InputSanitizer.safeUrl(raw);
                if (url == null) throw new BadRequestException(key.label() + " must be a full http(s):// link");
                yield url;
            }
        };
        if (value == null) value = "";
        if (key.required() && value.isBlank()) {
            throw new BadRequestException(key.label() + " is required");
        }
        if (value.length() > key.maxLength()) {
            throw new BadRequestException(key.label() + " must be at most " + key.maxLength() + " characters");
        }
        if (!value.isEmpty() && key.type() == ContentKeyRegistry.ValueType.EMAIL && !EMAIL.matcher(value).matches()) {
            throw new BadRequestException(key.label() + " must be a valid email address");
        }
        if (!value.isEmpty() && key.type() == ContentKeyRegistry.ValueType.PHONE && !PHONE.matcher(value).matches()) {
            throw new BadRequestException(key.label() + " must be a valid phone number");
        }
        return value;
    }
}
