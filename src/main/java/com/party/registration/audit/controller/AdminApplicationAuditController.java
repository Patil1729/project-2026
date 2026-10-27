package com.party.registration.audit.controller;

import com.party.common.dto.PageResponse;
import com.party.registration.audit.dto.ApplicationAuditResponse;
import com.party.registration.audit.service.ApplicationAuditService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/registration-applications/{applicationId}/history")
public class AdminApplicationAuditController {
    private final ApplicationAuditService service;

    @GetMapping
    public ResponseEntity<PageResponse<ApplicationAuditResponse>> history(@PathVariable("applicationId") @Positive Long id,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getAdminHistory(id, page, size));
    }
}
