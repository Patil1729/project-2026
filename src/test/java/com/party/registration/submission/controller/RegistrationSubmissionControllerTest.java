package com.party.registration.submission.controller;

import com.party.exception.GlobalExceptionHandler;
import com.party.registration.submission.dto.RegistrationSubmissionResponse;
import com.party.registration.document.entity.DocumentType;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.submission.service.RegistrationSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationSubmissionControllerTest {
    private RegistrationSubmissionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RegistrationSubmissionService.class);

        mvc = MockMvcBuilders.standaloneSetup(new RegistrationSubmissionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void submitNeedsNoBodyAndUsesThePrincipal() throws Exception {
        when(service.submit("applicant", 10L)).thenReturn(response());
        mvc.perform(post("/api/registration-applications/10/submit").principal(() -> "applicant"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void submissionUsesExactlyTheAuthenticatedPrincipal() throws Exception {

        when(service.submit("real.applicant", 10L)).thenReturn(response());
        mvc.perform(post("/api/registration-applications/10/submit").principal(() -> "real.applicant"))
                .andExpect(status().isOk());
        verify(service).submit("real.applicant", 10L);
    }

    @Test
    void ownerCanGetTheApprovalStageList() throws Exception {
        when(service.getApprovalSteps("applicant", 10L)).thenReturn(List.of());
        mvc.perform(get("/api/registration-applications/10/approval-steps").principal(() -> "applicant"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void conflictUsesTheExistingErrorResponseFormat() throws Exception {
        when(service.submit("applicant", 10L)).thenThrow(new RegistrationException(
                HttpStatus.CONFLICT, "REQUIRED_DOCUMENT_MISSING", "Upload recommendation"));
        mvc.perform(post("/api/registration-applications/10/submit").principal(() -> "applicant"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("REQUIRED_DOCUMENT_MISSING"));
    }

    @Test
    void anotherOwnersApplicationReturns404() throws Exception {
        when(service.getApprovalSteps("applicant", 99L)).thenThrow(new RegistrationException(
                HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Not found"));
        mvc.perform(get("/api/registration-applications/99/approval-steps").principal(() -> "applicant"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidIdentifierIsBadRequest() throws Exception {
        mvc.perform(post("/api/registration-applications/abc/submit").principal(() -> "applicant"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private RegistrationSubmissionResponse response() {
        return new RegistrationSubmissionResponse(10L, "REG-EXAMPLE", RegistrationStatus.UNDER_REVIEW,
                Instant.parse("2026-10-19T17:00:00Z"), List.of(DocumentType.RECOMMENDATION_LETTER), List.of());
    }

    @Test
    void missingBlankOrNamelessPrincipalNeverReachesService() throws Exception {
        mvc.perform(get("/api/registration-applications/10/approval-steps")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/registration-applications/10/approval-steps").principal(() -> "  ")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/registration-applications/10/approval-steps").principal(() -> null)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
