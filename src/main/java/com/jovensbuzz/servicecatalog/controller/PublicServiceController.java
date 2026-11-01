package com.jovensbuzz.servicecatalog.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.servicecatalog.dto.ServiceItemDto;
import com.jovensbuzz.servicecatalog.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "Public Services", description = "Public service catalog")
public class PublicServiceController {

    private final ServiceCatalogService serviceCatalogService;

    public PublicServiceController(ServiceCatalogService serviceCatalogService) {
        this.serviceCatalogService = serviceCatalogService;
    }

    @GetMapping
    @Operation(summary = "Get published services", description = "Retrieves all published services ordered by display order")
    public ResponseEntity<ApiResponse<List<ServiceItemDto>>> getPublishedServices() {
        List<ServiceItemDto> services = serviceCatalogService.getPublishedServices();
        return ResponseEntity.ok(ApiResponse.success(services));
    }
}
