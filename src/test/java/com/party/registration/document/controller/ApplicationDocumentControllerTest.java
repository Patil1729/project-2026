package com.party.registration.document.controller;

import com.party.registration.document.dto.ApplicationDocumentDownload;
import com.party.registration.document.dto.ApplicationDocumentResponse;
import com.party.registration.document.entity.DocumentStatus;
import com.party.registration.document.entity.DocumentType;
import com.party.exception.GlobalExceptionHandler;
import com.party.registration.document.service.ApplicationDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationDocumentControllerTest {
    private ApplicationDocumentService service;
    private MockEnvironment environment;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ApplicationDocumentService.class);
        environment = new MockEnvironment();
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationDocumentController(service, environment))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void multipartUploadUsesPrincipalAndReturnsMetadataWithoutStorageKey() throws Exception {
        when(service.upload(eq("shubham.patil"), eq(1L), any())).thenReturn(response());

        mvc.perform(multipart("/api/registration-applications/1/documents")
                        .file(file()).param("documentType", "IDENTITY_PROOF")
                        .principal(() -> "shubham.patil"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/registration-applications/1/documents/100"))
                .andExpect(jsonPath("$.documentVersion").value(1))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void localProfileAllowsNoAuthUsingConfiguredAccount() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-test-username", "shubham.patil");
        when(service.upload(eq("shubham.patil"), eq(1L), any())).thenReturn(response());

        mvc.perform(multipart("/api/registration-applications/1/documents")
                        .file(file()).param("documentType", "IDENTITY_PROOF"))
                .andExpect(status().isCreated());
        verify(service).upload(eq("shubham.patil"), eq(1L), any());
    }

    @Test
    void noPrincipalOutsideLocalProfileIsUnauthorized() throws Exception {
        environment.setProperty("app.registration.local-test-username", "shubham.patil");
        mvc.perform(multipart("/api/registration-applications/1/documents")
                        .file(file()).param("documentType", "IDENTITY_PROOF"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingFileAndUnknownDocumentType() throws Exception {
        mvc.perform(multipart("/api/registration-applications/1/documents")
                        .param("documentType", "IDENTITY_PROOF").principal(() -> "shubham.patil"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/registration-applications/1/documents")
                        .file(file()).param("documentType", "UNKNOWN").principal(() -> "shubham.patil"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rawJsonIsNotAnUploadRequest() throws Exception {
        mvc.perform(post("/api/registration-applications/1/documents")
                        .principal(() -> "shubham.patil").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"IDENTITY_PROOF\"}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    @Test
    void listReturnsArrayWithoutPagination() throws Exception {
        when(service.list("shubham.patil", 1L)).thenReturn(List.of(response()));
        mvc.perform(get("/api/registration-applications/1/documents").principal(() -> "shubham.patil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(100));
    }

    @Test
    void downloadReturnsAttachmentWithPrivateCacheHeaders() throws Exception {
        byte[] bytes = "%PDF-1.4\nfixture\n%%EOF".getBytes(StandardCharsets.UTF_8);
        when(service.download("shubham.patil", 1L, 100L)).thenReturn(
                new ApplicationDocumentDownload("proof.pdf", "application/pdf", bytes.length,
                        new ByteArrayInputStream(bytes)));

        mvc.perform(get("/api/registration-applications/1/documents/100/download")
                        .principal(() -> "shubham.patil"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment;")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "proof.pdf", "application/pdf",
                "%PDF-1.4\nfixture\n%%EOF".getBytes(StandardCharsets.UTF_8));
    }

    private ApplicationDocumentResponse response() {
        return new ApplicationDocumentResponse(100L, 1L, DocumentType.IDENTITY_PROOF, 1,
                "proof.pdf", "application/pdf", 23L, "a".repeat(64), DocumentStatus.UPLOADED,
                1L, Instant.parse("2026-10-18T15:00:00Z"), null, null, null);
    }
}
