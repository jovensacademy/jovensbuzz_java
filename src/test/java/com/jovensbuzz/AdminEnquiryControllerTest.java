package com.jovensbuzz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovensbuzz.common.response.PagedResponse;
import com.jovensbuzz.config.SecurityConfig;
import com.jovensbuzz.enquiry.controller.AdminEnquiryController;
import com.jovensbuzz.enquiry.dto.EnquiryDetailResponseDto;
import com.jovensbuzz.enquiry.dto.EnquiryResponseDto;
import com.jovensbuzz.enquiry.dto.EnquiryStatusUpdateRequest;
import com.jovensbuzz.enquiry.service.EnquiryService;
import com.jovensbuzz.integration.email.service.EmailNotificationService;
import com.jovensbuzz.security.CustomUserDetailsService;
import com.jovensbuzz.security.JwtAuthenticationFilter;
import com.jovensbuzz.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminEnquiryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
public class AdminEnquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnquiryService enquiryService;

    @MockBean
    private EmailNotificationService emailNotificationService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enquiries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(enquiryService);
    }

    @Test
    void forgedToken_returnsUnauthorized() throws Exception {
        when(jwtTokenProvider.parseClaims("forged")).thenReturn(null);

        mockMvc.perform(get("/api/v1/admin/enquiries").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedToken_returnsUnauthorized() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("jovens_buzz_2026");
        when(claims.get("tv", Integer.class)).thenReturn(0);
        when(jwtTokenProvider.parseClaims("old-token")).thenReturn(claims);
        // token version no longer matches (admin logged out or changed password)
        when(userDetailsService.loadForToken("jovens_buzz_2026", 0)).thenReturn(null);

        mockMvc.perform(get("/api/v1/admin/enquiries").header("Authorization", "Bearer old-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void nonAdminRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enquiries"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "jovens_buzz_2026", authorities = {"ROLE_MANAGER", "PERM_ENQUIRIES_VIEW"})
    void list_passesFiltersAndClampsPagination() throws Exception {
        when(enquiryService.getEnquiries(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(new EnquiryResponseDto()), 2, 100, 201, 3, false));

        mockMvc.perform(get("/api/v1/admin/enquiries")
                        .param("search", "acme")
                        .param("status", "NEW")
                        .param("service", "Brand Identity")
                        .param("page", "2")
                        .param("size", "5000")
                        .param("sortBy", "passwordHash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(201));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(enquiryService).getEnquiries(eq("acme"), eq("NEW"), eq("Brand Identity"), isNull(), isNull(), isNull(), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize(), "page size is capped");
        assertEquals("createdAt", pageable.getValue().getSort().iterator().next().getProperty(), "unknown sort field ignored");
    }

    @Test
    @WithMockUser(username = "jovens_buzz_2026", authorities = {"ROLE_MANAGER", "PERM_ENQUIRIES_VIEW", "PERM_ENQUIRIES_MANAGE"})
    void updateStatus_asAdmin_returnsOk() throws Exception {
        EnquiryStatusUpdateRequest request = new EnquiryStatusUpdateRequest("CONTACTED", "Called client");

        EnquiryDetailResponseDto detailDto = new EnquiryDetailResponseDto();
        detailDto.setId(1L);
        detailDto.setFullName("Jane Smith");
        detailDto.setStatus("CONTACTED");

        when(enquiryService.updateStatus(eq(1L), any(EnquiryStatusUpdateRequest.class), eq("jovens_buzz_2026")))
                .thenReturn(detailDto);

        mockMvc.perform(patch("/api/v1/admin/enquiries/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONTACTED"));
    }

    @Test
    @WithMockUser(username = "jovens_buzz_2026", authorities = {"ROLE_MANAGER", "PERM_ENQUIRIES_VIEW", "PERM_ENQUIRIES_MANAGE"})
    void updateStatus_blankStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/enquiries/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    @WithMockUser(username = "jovens_buzz_2026", authorities = {"ROLE_MANAGER", "PERM_ENQUIRIES_VIEW"})
    void nonNumericId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enquiries/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "jovens_buzz_2026", authorities = {"ROLE_MANAGER", "PERM_ENQUIRIES_VIEW"})
    void viewOnlyManager_cannotChangeStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/enquiries/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(enquiryService);
    }

    @Test
    @WithMockUser(username = "jovens_buzz_2026", authorities = {"ROLE_MANAGER", "PERM_ENQUIRIES_VIEW"})
    void managerWithoutExportPermission_cannotExport() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enquiries/export")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/enquiries/export/excel")).andExpect(status().isForbidden());
        verifyNoInteractions(enquiryService);
    }
}
