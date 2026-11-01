package com.jovensbuzz.faq.service;

import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.validation.InputSanitizer;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.ResourceNotFoundException;
import com.jovensbuzz.faq.dto.FaqItemDto;
import com.jovensbuzz.faq.entity.FaqItem;
import com.jovensbuzz.faq.repository.FaqItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FaqService {

    private final FaqItemRepository faqItemRepository;
    private final AuditLogService auditLogService;

    public FaqService(FaqItemRepository faqItemRepository, AuditLogService auditLogService) {
        this.faqItemRepository = faqItemRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<FaqItemDto> getPublishedFaqs() {
        return faqItemRepository.findByPublishedTrueOrderByDisplayOrderAsc()
                .stream().map(FaqItemDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FaqItemDto> getAllFaqs() {
        return faqItemRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(FaqItemDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FaqItemDto getFaqById(Long id) {
        FaqItem item = faqItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FAQ", "id", id));
        return FaqItemDto.fromEntity(item);
    }

    @Transactional
    public FaqItemDto createFaq(FaqItemDto dto, String username) {
        FaqItem item = new FaqItem();
        item.setQuestion(requireText(InputSanitizer.plainText(dto.getQuestion()), "Question"));
        item.setAnswer(requireText(InputSanitizer.multiLineText(dto.getAnswer()), "Answer"));
        item.setDisplayOrder(dto.getDisplayOrder());
        item.setPublished(dto.isPublished());

        FaqItem saved = faqItemRepository.save(item);
        auditLogService.logAction(username, "CREATE_FAQ", "FAQ", String.valueOf(saved.getId()), "Created FAQ: " + saved.getQuestion());
        return FaqItemDto.fromEntity(saved);
    }

    @Transactional
    public FaqItemDto updateFaq(Long id, FaqItemDto dto, String username) {
        FaqItem item = faqItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FAQ", "id", id));

        item.setQuestion(requireText(InputSanitizer.plainText(dto.getQuestion()), "Question"));
        item.setAnswer(requireText(InputSanitizer.multiLineText(dto.getAnswer()), "Answer"));
        item.setDisplayOrder(dto.getDisplayOrder());
        item.setPublished(dto.isPublished());

        FaqItem updated = faqItemRepository.save(item);
        auditLogService.logAction(username, "UPDATE_FAQ", "FAQ", String.valueOf(updated.getId()), "Updated FAQ: " + updated.getQuestion());
        return FaqItemDto.fromEntity(updated);
    }

    @Transactional
    public void deleteFaq(Long id, String username) {
        FaqItem item = faqItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FAQ", "id", id));

        faqItemRepository.delete(item);
        auditLogService.logAction(username, "DELETE_FAQ", "FAQ", String.valueOf(id), "Deleted FAQ: " + item.getQuestion());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value;
    }
}
