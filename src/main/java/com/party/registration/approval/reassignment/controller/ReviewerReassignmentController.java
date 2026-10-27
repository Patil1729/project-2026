package com.party.registration.approval.reassignment.controller;

import com.party.registration.approval.reassignment.dto.*;
import com.party.registration.approval.reassignment.service.ReviewerReassignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/registration-applications/{applicationId}/reassign-reviewer")
public class ReviewerReassignmentController {
    private final ReviewerReassignmentService service;

    @PostMapping
    public ResponseEntity<ReviewerReassignmentResponse> reassign(@PathVariable("applicationId") @Positive Long id,
            @Valid @RequestBody ReviewerReassignmentRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reassign(id, request));
    }
}
