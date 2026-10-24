package com.party.registration.approval.controller;

import com.party.exception.GlobalExceptionHandler;
import com.party.registration.approval.dto.ApplicationApprovalRequest;
import com.party.registration.approval.dto.ApplicationRejectionRequest;
import com.party.registration.approval.dto.ApplicationReviewInboxResponse;
import com.party.registration.approval.dto.ApplicationReviewResponse;
import com.party.registration.application.dto.RegistrationApplicationResponse;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.approval.service.ApplicationReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationReviewControllerTest {
    private static final String BASE = "/api/application-reviews";
    private static final String APPROVE = BASE + "/applications/10/steps/100/approve";
    private static final String REJECT = BASE + "/applications/10/steps/100/reject";
    private ApplicationReviewService service;
    private MockEnvironment environment;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ApplicationReviewService.class);
        environment = new MockEnvironment();
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationReviewController(service, environment))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void inboxUsesPrincipalDefaultPagingAndNoStore() throws Exception {
        when(service.getPending("head", 0, 20)).thenReturn(new ApplicationReviewInboxResponse(List.of(), 0, 20, false));
        mvc.perform(get(BASE + "/pending").principal(() -> "head"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void localProfileUsesReviewerIdentityAndNotApplicantIdentity() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-reviewer-username", " document.reviewer ");
        environment.setProperty("app.registration.local-test-username", "applicant");
        when(service.getPending("document.reviewer", 0, 20)).thenReturn(new ApplicationReviewInboxResponse(List.of(), 0, 20, false));
        mvc.perform(get(BASE + "/pending")).andExpect(status().isOk());
        verify(service).getPending("document.reviewer", 0, 20);
    }

    @Test
    void principalTakesPriorityOverLocalReviewer() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-reviewer-username", "local.head");
        when(service.getReview("real.head", 10L)).thenReturn(response());
        mvc.perform(get(BASE + "/applications/10").principal(() -> "real.head"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.application.status").value("UNDER_REVIEW"));
        verify(service).getReview("real.head", 10L);
    }

    @Test
    void localPropertyDoesNotAuthenticateOutsideLocalProfile() throws Exception {
        environment.setProperty("app.registration.local-reviewer-username", "head");
        mvc.perform(get(BASE + "/pending")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void blankLocalIdentityIsUnauthorized() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-reviewer-username", "  ");
        mvc.perform(get(BASE + "/pending")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void approveAcceptsEmptyJsonObjectAndReturnsTimeline() throws Exception {
        when(service.approve(eq("head"), eq(10L), eq(100L), any())).thenReturn(response());
        mvc.perform(patch(APPROVE).principal(() -> "head").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvalSteps").isArray())
                .andExpect(header().string("Cache-Control", "no-store"));
        ArgumentCaptor<ApplicationApprovalRequest> capture = ArgumentCaptor.forClass(ApplicationApprovalRequest.class);
        verify(service).approve(eq("head"), eq(10L), eq(100L), capture.capture());
        assertNull(capture.getValue().comment());
    }

    @Test
    void approveRequiresJsonRequestBody() throws Exception {
        mvc.perform(patch(APPROVE).principal(() -> "head").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectPassesRequiredReason() throws Exception {
        when(service.reject(eq("head"), eq(10L), eq(100L), any())).thenReturn(response());
        mvc.perform(patch(REJECT).principal(() -> "head").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Application details do not match\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<ApplicationRejectionRequest> capture = ArgumentCaptor.forClass(ApplicationRejectionRequest.class);
        verify(service).reject(eq("head"), eq(10L), eq(100L), capture.capture());
        assertEquals("Application details do not match", capture.getValue().reason());
    }

    @Test
    void rejectRequiresNonblankReason() throws Exception {
        mvc.perform(patch(REJECT).principal(() -> "head").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void oversizedCommentIsRejectedByRequestValidation() throws Exception {
        mvc.perform(patch(APPROVE).principal(() -> "head").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(patch(APPROVE).principal(() -> "head").contentType(MediaType.TEXT_PLAIN).content("Approved"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    @Test
    void invalidStepIdentifierReturns400() throws Exception {
        mvc.perform(patch(BASE + "/applications/10/steps/abc/approve").principal(() -> "head")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidPageSizeReturns400() throws Exception {
        mvc.perform(get(BASE + "/pending?size=101").principal(() -> "head"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void reviewConflictUsesExistingErrorFormat() throws Exception {
        when(service.approve(eq("head"), eq(10L), eq(100L), any())).thenThrow(
                new RegistrationException(HttpStatus.CONFLICT, "REVIEWER_MISSING", "Appoint the next head"));
        mvc.perform(patch(APPROVE).principal(() -> "head").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REVIEWER_MISSING"));
    }

    @Test
    void otherReviewersApplicationReturns404() throws Exception {
        when(service.getReview("head", 99L)).thenThrow(new RegistrationException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Not found"));
        mvc.perform(get(BASE + "/applications/99").principal(() -> "head")).andExpect(status().isNotFound());
    }

    @Test
    void seniorEscalatedInboxUsesPrincipalAndNoStore() throws Exception {
        when(service.getEscalated("senior", 0, 20)).thenReturn(new ApplicationReviewInboxResponse(List.of(), 0, 20, false));
        mvc.perform(get(BASE + "/escalated").principal(() -> "senior"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void escalatedInboxRequiresIdentity() throws Exception {
        mvc.perform(get(BASE + "/escalated")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }

    @Test
    void escalatedInboxRejectsInvalidPageSize() throws Exception {
        mvc.perform(get(BASE + "/escalated?size=101").principal(() -> "senior"))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }

    private ApplicationReviewResponse response() {
        return new ApplicationReviewResponse(RegistrationApplicationResponse.builder().id(10L)
                .status(RegistrationStatus.UNDER_REVIEW).build(), null, List.of());
    }
}
