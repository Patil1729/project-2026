package com.party.registration.admin.dashboard.service;

import com.party.registration.admin.common.dto.OrganizationScope;
import com.party.registration.admin.common.service.AdminOrganizationScopeService;
import com.party.registration.admin.dashboard.repository.AdminDashboardRepository;
import com.party.registration.application.entity.RegistrationStatus;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminDashboardServiceImplTest {
    @Test void totalsAndVacanciesUseTheSameScopeAndInstant() {
        var repository = mock(AdminDashboardRepository.class);
        var scopes = mock(AdminOrganizationScopeService.class);
        var scope = new OrganizationScope(2L, true, List.of(2L, 3L));
        var now = Instant.parse("2026-10-24T09:00:00Z");
        when(scopes.resolve(2L, true)).thenReturn(scope);
        var counts = new EnumMap<RegistrationStatus, Long>(RegistrationStatus.class);
        counts.put(RegistrationStatus.AWAITING_RECOMMENDATION, 2L);
        counts.put(RegistrationStatus.UNDER_REVIEW, 3L);
        counts.put(RegistrationStatus.APPROVED, 4L);
        counts.put(RegistrationStatus.REJECTED, 1L);
        when(repository.applicationCounts(scope)).thenReturn(counts);
        when(repository.activePositions(scope, false)).thenReturn(20L);
        when(repository.activePositions(scope, true)).thenReturn(7L);
        when(repository.openSteps(scope, now, true, false)).thenReturn(2L);
        var service = new AdminDashboardServiceImpl(repository, scopes, Clock.fixed(now, ZoneOffset.UTC));
        var result = service.getDashboard(2L, true);
        assertEquals(10, result.totalApplications());
        assertEquals(5, result.pendingApplications());
        assertEquals(13, result.vacantPositions());
        assertEquals(2, result.overdueReviewSteps());
        verify(repository).openSteps(scope, now, false, false);
        verify(repository).openSteps(scope, now, false, true);
    }
}
