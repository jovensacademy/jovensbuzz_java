package com.jovensbuzz.servicecatalog.controller;

import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.servicecatalog.dto.ServiceItemDto;
import com.jovensbuzz.servicecatalog.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/services")
@Tag(name = "Admin Services", description = "Administration of agency services")
@SecurityRequirement(name = "bearerAuth")
public class AdminServiceController {

    private final ServiceCatalogService serviceCatalogService;

    public AdminServiceController(ServiceCatalogService serviceCatalogService) {
        this.serviceCatalogService = serviceCatalogService;
    }

    @GetMapping
    @Operation(summary = "Get all services", description = "Retrieves all services (both published and draft)")
    public ResponseEntity<ApiResponse<List<ServiceItemDto>>> getAllServices() {
        List<ServiceItemDto> services = serviceCatalogService.getAllServices();
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get service by ID", description = "Retrieves a single service by its ID")
    public ResponseEntity<ApiResponse<ServiceItemDto>> getServiceById(@PathVariable Long id) {
        ServiceItemDto service = serviceCatalogService.getServiceById(id);
        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @PostMapping
    @Operation(summary = "Create service", description = "Creates a new service")
    public ResponseEntity<ApiResponse<ServiceItemDto>> createService(
            @Valid @RequestBody ServiceItemDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "ADMIN";
        ServiceItemDto created = serviceCatalogService.createService(dto, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Service created successfully", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update service", description = "Updates an existing service")
    public ResponseEntity<ApiResponse<ServiceItemDto>> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceItemDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "ADMIN";
        ServiceItemDto updated = serviceCatalogService.updateService(id, dto, username);
        return ResponseEntity.ok(ApiResponse.success("Service updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete service", description = "Deletes a service by ID")
    public ResponseEntity<ApiResponse<Void>> deleteService(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "ADMIN";
        serviceCatalogService.deleteService(id, username);
        return ResponseEntity.ok(ApiResponse.success("Service deleted successfully", null));
    }
}
