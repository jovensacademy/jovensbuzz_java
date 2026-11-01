package com.jovensbuzz.common;

import com.jovensbuzz.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "Public health endpoint")
public class HealthController {

    @GetMapping
    @Operation(summary = "Public health status", description = "Returns basic application health state")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Collections.singletonMap("status", "UP")));
    }
}
