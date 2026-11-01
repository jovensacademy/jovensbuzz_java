package com.jovensbuzz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovensbuzz.config.SecurityConfig;
import com.jovensbuzz.enquiry.controller.PublicEnquiryController;
import com.jovensbuzz.enquiry.dto.EnquiryCreateRequest;
import com.jovensbuzz.enquiry.dto.EnquirySubmissionResponse;
import com.jovensbuzz.enquiry.service.EnquiryService;
import com.jovensbuzz.security.CustomUserDetailsService;
import com.jovensbuzz.security.JwtAuthenticationFilter;
import com.jovensbuzz.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicEnquiryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
public class PublicEnquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnquiryService enquiryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private EnquiryCreateRequest validRequest() {
        EnquiryCreateRequest request = new EnquiryCreateRequest();
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("+1 555 123 4567");
        request.setCompanyName("Acme Corp");
        request.setServiceInterest("Creative Design");
        request.setMessage("We need a refreshed brand look for spring.");
        request.setPrivacyConsent(true);
        return request;
    }

    @Test
    void validPayload_returnsCreatedWithReferenceOnly() throws Exception {
        when(enquiryService.createEnquiry(any(EnquiryCreateRequest.class)))
                .thenReturn(new EnquirySubmissionResponse("JB-2026-ABC123", Instant.now(), false));

        mockMvc.perform(post("/api/v1/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicReference").value("JB-2026-ABC123"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void duplicateSubmission_returnsOkWithOriginalReference() throws Exception {
        when(enquiryService.createEnquiry(any(EnquiryCreateRequest.class)))
                .thenReturn(new EnquirySubmissionResponse("JB-2026-ABC123", Instant.now(), true));

        mockMvc.perform(post("/api/v1/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true))
                .andExpect(jsonPath("$.data.publicReference").value("JB-2026-ABC123"));
    }

    @Test
    void missingNameAndInvalidEmail_returnsFieldErrors() throws Exception {
        EnquiryCreateRequest request = validRequest();
        request.setFullName("");
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists());
        verify(enquiryService, never()).createEnquiry(any());
    }

    @Test
    void phoneWithoutCountryCode_isRejected() throws Exception {
        EnquiryCreateRequest request = validRequest();
        request.setPhone("5551234567");

        mockMvc.perform(post("/api/v1/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void submissionWithoutPrivacyConsent_isAccepted() throws Exception {
        when(enquiryService.createEnquiry(any(EnquiryCreateRequest.class)))
                .thenReturn(new EnquirySubmissionResponse("JB-2026-NOCONSENT", Instant.now(), false));

        EnquiryCreateRequest request = validRequest();
        request.setPrivacyConsent(false);
        request.setPrivacyPolicyVersion("2026-10");

        mockMvc.perform(post("/api/v1/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicReference").value("JB-2026-NOCONSENT"));

        verify(enquiryService).createEnquiry(argThat(req -> !req.isPrivacyConsent() && "2026-10".equals(req.getPrivacyPolicyVersion())));
    }

    @Test
    void malformedJson_returnsBadRequestWithoutInternals() throws Exception {
        mockMvc.perform(post("/api/v1/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request is malformed or contains invalid parameters"));
    }
}
