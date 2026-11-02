package com.party.registration.document.inbox.controller;

import com.party.common.dto.PageResponse;
import com.party.registration.application.dto.RegistrationApplicationResponse;
import com.party.registration.document.inbox.service.DocumentReviewInboxService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/document-reviews/applications")
public class DocumentReviewInboxController {
    private final DocumentReviewInboxService service;
    @GetMapping
    public ResponseEntity<PageResponse<RegistrationApplicationResponse>> inbox(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.inbox(page, size));
    }
    @GetMapping("/{id}")
    public ResponseEntity<RegistrationApplicationResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
    }
}
