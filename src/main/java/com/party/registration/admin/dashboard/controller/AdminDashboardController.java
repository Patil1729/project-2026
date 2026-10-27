package com.party.registration.admin.dashboard.controller;

import com.party.registration.admin.dashboard.dto.AdminDashboardResponse;
import com.party.registration.admin.dashboard.service.AdminDashboardService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService service;

    @GetMapping
    public ResponseEntity<AdminDashboardResponse> dashboard(
            @RequestParam(name = "organizationUnitId", required = false) @Positive Long unitId,
            @RequestParam(name = "includeDescendants", defaultValue = "true") boolean descendants) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getDashboard(unitId, descendants));
    }
}
