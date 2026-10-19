package com.party.registration.controller;

import com.party.exception.GlobalExceptionHandler;
import com.party.registration.dto.ApplicationDocumentDownload;
import com.party.registration.dto.ApplicationDocumentResponse;
import com.party.registration.entity.DocumentStatus;
import com.party.registration.entity.DocumentType;
import com.party.registration.exception.RegistrationException;
import com.party.registration.service.ApplicationDocumentReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationDocumentReviewControllerTest {
    private static final String BASE = "/api/document-reviews/applications/10/documents";
    private ApplicationDocumentReviewService service;
    private MockEnvironment environment;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ApplicationDocumentReviewService.class);
        environment = new MockEnvironment();
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationDocumentReviewController(service, environment))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void verifyUsesPrincipalAndReturnsReviewMetadata() throws Exception {
        when(service.verify(eq("head"), eq(10L), eq(100L), any())).thenReturn(response());
        mvc.perform(patch(BASE + "/100/verify").principal(() -> "head")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Checked\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.reviewedById").value(2))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
        verify(service).verify(eq("head"), eq(10L), eq(100L), argThat(r -> "Checked".equals(r.getComment())));
    }

    @Test
    void localReviewerIdentityIsSeparateFromTheApplicantIdentity() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-test-username", "shubham.patil");
        environment.setProperty("app.registration.local-reviewer-username", " document.reviewer ");
        when(service.list("document.reviewer", 10L)).thenReturn(List.of(response()));
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(100));
        verify(service).list("document.reviewer", 10L);
    }

    @Test
    void applicantTestIdentityIsNeverUsedAsAReviewerFallback() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-test-username", "shubham.patil");
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void reviewerPropertyHasNoEffectOutsideLocalProfile() throws Exception {
        environment.setProperty("app.registration.local-reviewer-username", "document.reviewer");
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void aRealPrincipalTakesPriorityOverTheLocalReviewer() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-reviewer-username", "document.reviewer");
        when(service.list("real.head", 10L)).thenReturn(List.of());
        mvc.perform(get(BASE).principal(() -> "real.head")).andExpect(status().isOk());
        verify(service).list("real.head", 10L);
    }

    @Test
    void rejectionRequiresANonblankReason() throws Exception {
        for (String body : List.of("{}", "{\"reason\":\"   \"}", "{\"reason\":null}")) {
            mvc.perform(patch(BASE + "/100/reject").principal(() -> "head")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void reviewCommentsAndReasonsAreLimitedToTwoThousandCharacters() throws Exception {
        String tooLong = "x".repeat(2001);
        mvc.perform(patch(BASE + "/100/verify").principal(() -> "head")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(BASE + "/100/reject").principal(() -> "head")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void verificationAllowsAnEmptyJsonObjectForAnOptionalComment() throws Exception {
        when(service.verify(eq("head"), eq(10L), eq(100L), any())).thenReturn(response());
        mvc.perform(patch(BASE + "/100/verify").principal(() -> "head")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void aMissingJsonBodyIsBadRequest() throws Exception {
        mvc.perform(patch(BASE + "/100/verify").principal(() -> "head")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectionForwardsTheReasonToTheService() throws Exception {
        when(service.reject(eq("head"), eq(10L), eq(100L), any())).thenReturn(response());
        mvc.perform(patch(BASE + "/100/reject").principal(() -> "head")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unreadable\"}"))
                .andExpect(status().isOk());
        verify(service).reject(eq("head"), eq(10L), eq(100L), argThat(r -> "Unreadable".equals(r.getReason())));
    }

    @Test
    void unsupportedRequestMediaReturns415() throws Exception {
        mvc.perform(patch(BASE + "/100/verify").principal(() -> "head")
                        .contentType(MediaType.TEXT_PLAIN).content("Checked"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    @Test
    void scopeErrorsUseTheExistingGlobalErrorFormat() throws Exception {
        when(service.list("head", 10L)).thenThrow(new RegistrationException(
                HttpStatus.FORBIDDEN, "DOCUMENT_REVIEW_FORBIDDEN", "Appointment required"));
        mvc.perform(get(BASE).principal(() -> "head"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("DOCUMENT_REVIEW_FORBIDDEN"))
                .andExpect(jsonPath("$.path").value(BASE));
    }

    @Test
    void reviewerDownloadUsesAttachmentAndPrivateCacheHeaders() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        when(service.download("head", 10L, 100L)).thenReturn(new ApplicationDocumentDownload(
                "proof.pdf", "application/pdf", 3L, new ByteArrayInputStream(bytes)));
        mvc.perform(get(BASE + "/100/download").principal(() -> "head"))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment;")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    private ApplicationDocumentResponse response() {
        return new ApplicationDocumentResponse(100L, 10L, DocumentType.IDENTITY_PROOF, 2,
                "proof.pdf", "application/pdf", 23L, "a".repeat(64), DocumentStatus.VERIFIED,
                1L, Instant.parse("2026-10-18T10:00:00Z"), 2L,
                Instant.parse("2026-10-19T10:00:00Z"), "Checked");
    }
}
