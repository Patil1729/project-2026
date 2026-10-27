package com.party.registration.admin.controller;

import com.party.common.dto.PageResponse;
import com.party.committee.appointment.controller.AppointmentController;
import com.party.committee.appointment.dto.*;
import com.party.committee.appointment.service.AppointmentService;
import com.party.committee.entity.CommitteePositionType;
import com.party.exception.GlobalExceptionHandler;
import com.party.registration.admin.dashboard.controller.AdminDashboardController;
import com.party.registration.admin.dashboard.dto.AdminDashboardResponse;
import com.party.registration.admin.dashboard.service.AdminDashboardService;
import com.party.registration.admin.search.controller.AdminApplicationSearchController;
import com.party.registration.admin.search.service.AdminApplicationSearchService;
import com.party.registration.audit.controller.ApplicationAuditController;
import com.party.registration.audit.service.ApplicationAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BackendAdministrationControllerTest {
    @Test void searchReturnsStablePagingWithoutRequiringAnAuthenticationModule() throws Exception {
        var service = mock(AdminApplicationSearchService.class);
        when(service.search(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false));
        var mvc = MockMvcBuilders.standaloneSetup(new AdminApplicationSearchController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/admin/registration-applications").param("status", "UNDER_REVIEW"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isArray()).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/admin/registration-applications").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/registration-applications").param("createdFrom", "2026-10-24")
                .param("createdTo", "2026-10-01")).andExpect(status().isBadRequest());
    }

    @Test void dashboardUsesOptionalOrganizationScope() throws Exception {
        var service = mock(AdminDashboardService.class);
        when(service.getDashboard(2L, true)).thenReturn(new AdminDashboardResponse(Instant.now(), 2L, true,
                0, Map.of(), 0, 0, 0, 0, 1, 5, 2, 3));
        var mvc = MockMvcBuilders.standaloneSetup(new AdminDashboardController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/admin/dashboard").param("organizationUnitId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vacantPositions").value(3));
    }

    @Test void bootstrapReturnsLocationAndRejectsBlankReasons() throws Exception {
        var service = mock(AppointmentService.class);
        when(service.bootstrapStatePresident(any())).thenReturn(new AppointmentResponse(9L, 3L, "head", "State Head",
                2L, "State committee", 2L, 4L, CommitteePositionType.PRESIDENT, 1, LocalDate.now(), null, true, null));
        var mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/advocate-assignments/bootstrap-state-president").contentType(MediaType.APPLICATION_JSON)
                .content("{\"committeeId\":2,\"advocateUserId\":3,\"reason\":\"Initial appointment\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/advocate-assignments/9"));
        mvc.perform(patch("/api/advocate-assignments/9/end").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        verify(service, never()).end(anyLong(), any());
    }

    @Test void applicantHistoryUsesTheExistingLocalProfileIdentity() throws Exception {
        var service = mock(ApplicationAuditService.class);
        when(service.getMyHistory("local.applicant", 1L, 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false));
        var environment = new MockEnvironment().withProperty("app.registration.local-test-username", "local.applicant");
        environment.setActiveProfiles("local");
        var mvc = MockMvcBuilders.standaloneSetup(new ApplicationAuditController(service, environment))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/registration-applications/1/history")).andExpect(status().isOk());
        verify(service).getMyHistory("local.applicant", 1L, 0, 20);
    }

    @Test void applicantHistoryWithoutAnIdentityReturns401OutsideLocal() throws Exception {
        var service = mock(ApplicationAuditService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ApplicationAuditController(service, new MockEnvironment()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/registration-applications/1/history")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
