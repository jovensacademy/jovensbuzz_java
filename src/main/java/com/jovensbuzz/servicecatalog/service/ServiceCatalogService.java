package com.jovensbuzz.servicecatalog.service;

import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.validation.InputSanitizer;
import com.jovensbuzz.common.exception.ResourceNotFoundException;
import com.jovensbuzz.servicecatalog.dto.ServiceItemDto;
import com.jovensbuzz.servicecatalog.entity.ServiceItem;
import com.jovensbuzz.servicecatalog.repository.ServiceItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceCatalogService {

    private final ServiceItemRepository serviceItemRepository;
    private final AuditLogService auditLogService;

    public ServiceCatalogService(
            ServiceItemRepository serviceItemRepository,
            AuditLogService auditLogService) {
        this.serviceItemRepository = serviceItemRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ServiceItemDto> getPublishedServices() {
        return serviceItemRepository.findByPublishedTrueOrderByDisplayOrderAsc()
                .stream().map(ServiceItemDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceItemDto> getAllServices() {
        return serviceItemRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(ServiceItemDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceItemDto getServiceById(Long id) {
        ServiceItem item = serviceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));
        return ServiceItemDto.fromEntity(item);
    }

    @Transactional
    public ServiceItemDto createService(ServiceItemDto dto, String username) {
        if (serviceItemRepository.existsBySlug(dto.getSlug())) {
            throw new BadRequestException("A service with slug '" + dto.getSlug() + "' already exists");
        }

        ServiceItem item = new ServiceItem();
        item.setTitle(requireText(InputSanitizer.plainText(dto.getTitle()), "Title"));
        item.setSlug(dto.getSlug().trim().toLowerCase());
        item.setDescription(requireText(InputSanitizer.multiLineText(dto.getDescription()), "Description"));
        item.setIcon(dto.getIcon() == null || dto.getIcon().isBlank() ? null : dto.getIcon().trim());
        item.setDisplayOrder(dto.getDisplayOrder());
        item.setPublished(dto.isPublished());

        ServiceItem saved = serviceItemRepository.save(item);

        auditLogService.logAction(username, "CREATE_SERVICE", "SERVICE", String.valueOf(saved.getId()), "Created service: " + saved.getTitle());
        return ServiceItemDto.fromEntity(saved);
    }

    @Transactional
    public ServiceItemDto updateService(Long id, ServiceItemDto dto, String username) {
        ServiceItem item = serviceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));

        if (!item.getSlug().equals(dto.getSlug().trim().toLowerCase()) && serviceItemRepository.existsBySlug(dto.getSlug())) {
            throw new BadRequestException("A service with slug '" + dto.getSlug() + "' already exists");
        }

        item.setTitle(requireText(InputSanitizer.plainText(dto.getTitle()), "Title"));
        item.setSlug(dto.getSlug().trim().toLowerCase());
        item.setDescription(requireText(InputSanitizer.multiLineText(dto.getDescription()), "Description"));
        item.setIcon(dto.getIcon() == null || dto.getIcon().isBlank() ? null : dto.getIcon().trim());
        item.setDisplayOrder(dto.getDisplayOrder());
        item.setPublished(dto.isPublished());

        ServiceItem updated = serviceItemRepository.save(item);

        auditLogService.logAction(username, "UPDATE_SERVICE", "SERVICE", String.valueOf(updated.getId()), "Updated service: " + updated.getTitle());
        return ServiceItemDto.fromEntity(updated);
    }

    @Transactional
    public void deleteService(Long id, String username) {
        ServiceItem item = serviceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));

        serviceItemRepository.delete(item);
        auditLogService.logAction(username, "DELETE_SERVICE", "SERVICE", String.valueOf(id), "Deleted service: " + item.getTitle());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value;
    }
}
