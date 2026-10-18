package com.party.registration.controller;

import com.party.registration.dto.RegistrationApplicationResponse;
import com.party.registration.entity.RegistrationStatus;
import com.party.exception.GlobalExceptionHandler;
import com.party.registration.service.RegistrationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationApplicationControllerTest {
    private RegistrationApplicationService service;
    private MockMvc mvc;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        service = mock(RegistrationApplicationService.class);
        environment = new MockEnvironment();
        mvc = MockMvcBuilders.standaloneSetup(new RegistrationApplicationController(service, environment))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void requiresAuthenticatedPrincipal() throws Exception {
        mvc.perform(post("/api/registration-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedShakhaId\":2,\"requestedPositionId\":20}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingAndNonpositiveIds() throws Exception {
        mvc.perform(post("/api/registration-applications")
                        .principal(() -> "shubham")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedShakhaId\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void usesPrincipalAsApplicantAndReturnsCreatedLocation() throws Exception {
        when(service.createApplication(eq("shubham"), any())).thenReturn(
                RegistrationApplicationResponse.builder().id(50L)
                        .status(RegistrationStatus.AWAITING_RECOMMENDATION).build());

        mvc.perform(post("/api/registration-applications")
                        .principal(() -> "shubham")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedShakhaId\":2,\"requestedPositionId\":20}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/registration-applications/50"));
        verify(service).createApplication(eq("shubham"), any());
    }

    @Test
    void requiresAuthenticationForSingleApplicationLookup() throws Exception {
        mvc.perform(get("/api/registration-applications/50"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void requiresAuthenticationForApplicationList() throws Exception {
        mvc.perform(get("/api/registration-applications/my"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void returnsApplicationsAsJsonArrayForAuthenticatedUser() throws Exception {
        when(service.getMyApplications("shubham")).thenReturn(List.of(
                RegistrationApplicationResponse.builder().id(50L)
                        .status(RegistrationStatus.AWAITING_RECOMMENDATION).build(),
                RegistrationApplicationResponse.builder().id(40L)
                        .status(RegistrationStatus.UNDER_REVIEW).build()));

        mvc.perform(get("/api/registration-applications/my").principal(() -> "shubham"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[1].id").value(40));
        verify(service).getMyApplications("shubham");
    }

    @Test
    void localProfileCanCreateApplicationUsingConfiguredTestAccount() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-test-username", "shubham.patil");
        when(service.createApplication(eq("shubham.patil"), any())).thenReturn(
                RegistrationApplicationResponse.builder().id(50L)
                        .status(RegistrationStatus.AWAITING_RECOMMENDATION).build());

        mvc.perform(post("/api/registration-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedShakhaId\":3,\"requestedPositionId\":20}"))
                .andExpect(status().isCreated());
        verify(service).createApplication(eq("shubham.patil"), any());
    }

    @Test
    void configuredTestAccountCannotBypassAuthenticationWithoutLocalProfile() throws Exception {
        environment.setActiveProfiles("prod");
        environment.setProperty("app.registration.local-test-username", "shubham.patil");

        mvc.perform(get("/api/registration-applications/my"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void localProfileWithoutTestAccountStillRequiresAuthentication() throws Exception {
        environment.setActiveProfiles("local");

        mvc.perform(get("/api/registration-applications/my"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedPrincipalTakesPrecedenceOverLocalTestAccount() throws Exception {
        environment.setActiveProfiles("local");
        environment.setProperty("app.registration.local-test-username", "shubham.patil");
        when(service.getMyApplications("another.user")).thenReturn(List.of());

        mvc.perform(get("/api/registration-applications/my").principal(() -> "another.user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        verify(service).getMyApplications("another.user");
        verify(service, never()).getMyApplications("shubham.patil");
    }
}
