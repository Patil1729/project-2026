package com.party.registration.admin.dashboard.service;

import com.party.registration.admin.common.service.AdminOrganizationScopeService;
import com.party.registration.admin.dashboard.dto.AdminDashboardResponse;
import com.party.registration.admin.dashboard.repository.AdminDashboardRepository;
import com.party.registration.application.entity.RegistrationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final AdminDashboardRepository repository;
    private final AdminOrganizationScopeService scopes;
    private final Clock clock;

    public AdminDashboardResponse getDashboard(Long unitId, boolean includeDescendants) {
        var scope = scopes.resolve(unitId, includeDescendants);
        Instant now = clock.instant();
        var counts = repository.applicationCounts(scope);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long pending = counts.getOrDefault(RegistrationStatus.AWAITING_RECOMMENDATION, 0L)
                + counts.getOrDefault(RegistrationStatus.UNDER_REVIEW, 0L);
        long positions = repository.activePositions(scope, false);
        long occupied = repository.activePositions(scope, true);
        return new AdminDashboardResponse(now, unitId, includeDescendants, total,
                Collections.unmodifiableMap(counts), pending, repository.openSteps(scope, now, false, false),
                repository.openSteps(scope, now, true, false), repository.openSteps(scope, now, false, true),
                repository.activeCommittees(scope), positions, occupied, positions - occupied);
    }
}
