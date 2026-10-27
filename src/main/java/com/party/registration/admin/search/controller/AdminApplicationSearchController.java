package com.party.registration.admin.search.controller;

import com.party.common.dto.PageResponse;
import com.party.registration.admin.search.dto.*;
import com.party.registration.admin.search.service.AdminApplicationSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/registration-applications")
public class AdminApplicationSearchController {
    private final AdminApplicationSearchService service;

    @GetMapping
    public ResponseEntity<PageResponse<AdminApplicationResponse>> search(@Valid @ModelAttribute ApplicationSearchRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(request));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<AdminApplicationResponse> get(@PathVariable("applicationId") @Positive Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getById(id));
    }
}
