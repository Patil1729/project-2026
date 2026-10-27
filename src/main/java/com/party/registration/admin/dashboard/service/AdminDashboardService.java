package com.party.registration.admin.dashboard.service;

import com.party.registration.admin.dashboard.dto.AdminDashboardResponse;

public interface AdminDashboardService {
    AdminDashboardResponse getDashboard(Long organizationUnitId, boolean includeDescendants);
}
