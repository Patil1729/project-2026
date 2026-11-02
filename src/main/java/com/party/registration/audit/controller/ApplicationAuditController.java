package com.party.registration.audit.controller;

import com.party.common.dto.PageResponse;
import com.party.registration.audit.dto.ApplicationAuditResponse;
import com.party.registration.audit.service.ApplicationAuditService;
import com.party.registration.common.exception.RegistrationException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/registration-applications/{applicationId}/history")
public class ApplicationAuditController {
    private final ApplicationAuditService service;

    @GetMapping
    public ResponseEntity<PageResponse<ApplicationAuditResponse>> history(Principal principal,
            @PathVariable("applicationId") @Positive Long id,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getMyHistory(username(principal), id, page, size));
    }

    private String username(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new RegistrationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Sign in before accessing this resource.");
        }
        return principal.getName();
    }
}
