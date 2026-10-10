package com.party.organization.controller;

import com.party.organization.dto.OrganizationUnitRequest;
import com.party.organization.dto.OrganizationUnitResponse;
import com.party.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organization-units")
@RequiredArgsConstructor
public class OrganizationUnitController {

    private final OrganizationService organizationUnitService;

    @PostMapping
    public ResponseEntity<OrganizationUnitResponse> create(
            @Valid @RequestBody OrganizationUnitRequest request) {

        OrganizationUnitResponse response =
                organizationUnitService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationUnitResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                organizationUnitService.getById(id)
        );
    }

    @GetMapping
    public ResponseEntity<List<OrganizationUnitResponse>> getAll() {

        return ResponseEntity.ok(
                organizationUnitService.getAll()
        );
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<List<OrganizationUnitResponse>> getChildren(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                organizationUnitService.getChildren(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrganizationUnitResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody OrganizationUnitRequest request) {

        return ResponseEntity.ok(
                organizationUnitService.update(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {

        organizationUnitService.delete(id);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping ("/{id}/activate")
    public ResponseEntity<String> active(
            @PathVariable Long id) {

        organizationUnitService.active(id);

        return ResponseEntity.ok("Activation successful");
    }

    @GetMapping("/roots")
    public ResponseEntity<List<OrganizationUnitResponse>> getRootUnits() {

        return ResponseEntity.ok(
                organizationUnitService.getRootUnits()
        );
    }
}