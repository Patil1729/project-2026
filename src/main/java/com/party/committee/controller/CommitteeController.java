package com.party.committee.controller;

import com.party.committee.dto.CommitteeRequest;
import com.party.committee.dto.CommitteeResponse;
import com.party.committee.service.CommitteeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/committees")
@RequiredArgsConstructor
public class CommitteeController {


    private final CommitteeService committeeService;

    @PostMapping
    public ResponseEntity<CommitteeResponse> createCommittee(@Valid @RequestBody CommitteeRequest committeeRequest) {
            CommitteeResponse committeeResponse = committeeService.createCommittee(committeeRequest);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(committeeResponse.getId()).toUri();
        return ResponseEntity.created(location).body(committeeResponse);
//        return ResponseEntity.status(HttpStatus.CREATED).body(committeeResponse);
    }

    @GetMapping
    public ResponseEntity<List<CommitteeResponse>> getAllCommittees() {
        return ResponseEntity.ok(committeeService.getAllCommittees());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommitteeResponse> getCommitteeById(@PathVariable Long id) {
        return ResponseEntity.ok(committeeService.getCommitteeById(id));
    }

    @GetMapping("/organization-units/{id}/committee")
    public ResponseEntity<CommitteeResponse> getCommitteeByOrganizationUnitId(@PathVariable("id") Long organizationUnitId) {
        return ResponseEntity.ok(committeeService.getCommitteeByOrganizationUnitId(organizationUnitId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommitteeResponse> updateCommittee(@PathVariable Long id, @Valid @RequestBody CommitteeRequest committeeRequest) {
        return ResponseEntity.ok(committeeService.updateCommittee(id, committeeRequest));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateCommittee(
            @PathVariable("id") Long id) {

        committeeService.activateCommittee(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateCommittee(
            @PathVariable("id") Long id) {

        committeeService.deactivateCommittee(id);
        return ResponseEntity.noContent().build();
    }

}
