package com.party.committee.appointment.controller;

import com.party.common.dto.PageResponse;
import com.party.committee.appointment.dto.*;
import com.party.committee.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/advocate-assignments")
public class AppointmentController {
    private final AppointmentService service;

    @PostMapping("/bootstrap-state-president")
    public ResponseEntity<AppointmentResponse> bootstrap(@Valid @RequestBody BootstrapPresidentRequest request) {
        var response = service.bootstrapStatePresident(request);
        return ResponseEntity.created(URI.create("/api/advocate-assignments/" + response.id()))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<AppointmentResponse>> search(@Valid @ModelAttribute AppointmentSearchRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(request));
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<AppointmentResponse> get(@PathVariable("assignmentId") @Positive Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getById(id));
    }

    @PatchMapping("/{assignmentId}/end")
    public ResponseEntity<AppointmentResponse> end(@PathVariable("assignmentId") @Positive Long id,
            @Valid @RequestBody EndAppointmentRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.end(id, request));
    }

    @PostMapping("/{assignmentId}/replace")
    public ResponseEntity<AppointmentReplacementResponse> replace(@PathVariable("assignmentId") @Positive Long id,
            @Valid @RequestBody ReplaceAppointmentRequest request) {
        var response = service.replace(id, request);
        return ResponseEntity.created(URI.create("/api/advocate-assignments/" + response.replacement().id()))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/{assignmentId}/history")
    public ResponseEntity<PageResponse<AppointmentAuditResponse>> history(@PathVariable("assignmentId") @Positive Long id,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(id, page, size));
    }
}
