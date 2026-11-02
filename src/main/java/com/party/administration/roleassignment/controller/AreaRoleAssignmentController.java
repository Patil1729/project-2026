package com.party.administration.roleassignment.controller;

import com.party.administration.access.policy.AreaScopePolicy;
import com.party.administration.roleassignment.dto.*;
import com.party.administration.roleassignment.service.AreaRoleAssignmentService;
import com.party.common.dto.PageResponse;
import com.party.organization.entity.OrganizationType;
import com.party.user.entity.RoleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/role-assignments")
@PreAuthorize("hasAuthority('PARTY_HEAD')")
public class AreaRoleAssignmentController {
    private final AreaRoleAssignmentService service;
    @GetMapping("/options")
    public Map<RoleType, OrganizationType> options() { return AreaScopePolicy.ROLE_TYPES; }
    @GetMapping
    public ResponseEntity<PageResponse<AreaRoleAssignmentResponse>> list(@RequestParam(required = false) @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(userId, page, size));
    }
    @PostMapping
    public ResponseEntity<AreaRoleAssignmentResponse> grant(@Valid @RequestBody AreaRoleGrantRequest request) {
        var response = service.grant(request);
        return ResponseEntity.created(URI.create("/api/admin/role-assignments/" + response.id())).cacheControl(CacheControl.noStore()).body(response);
    }
    @PatchMapping("/{id}/revoke")
    public ResponseEntity<AreaRoleAssignmentResponse> revoke(@PathVariable @Positive Long id, @Valid @RequestBody AreaRoleRevokeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.revoke(id, request));
    }
    @GetMapping("/{id}/history")
    public ResponseEntity<PageResponse<AreaRoleAuditResponse>> history(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(id, page, size));
    }
}
