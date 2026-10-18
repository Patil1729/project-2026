package com.party.electoral.controller;

import com.party.electoral.dto.AssemblyConstituencyRequest;
import com.party.electoral.dto.AssemblyConstituencyResponse;
import com.party.electoral.service.AssemblyConstituencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/assembly-constituencies")
@RequiredArgsConstructor
public class AssemblyConstituencyController {

    private final AssemblyConstituencyService constituencyService;

    @PostMapping
    public ResponseEntity<AssemblyConstituencyResponse> createConstituency(
            @Valid @RequestBody AssemblyConstituencyRequest request) {

        AssemblyConstituencyResponse response = constituencyService.createConstituency(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssemblyConstituencyResponse> getConstituency(
            @PathVariable("id") @Positive Long id) {

        return ResponseEntity.ok(constituencyService.getConstituencyById(id));
    }

    @GetMapping
    public ResponseEntity<List<AssemblyConstituencyResponse>> getActiveConstituencies(
            @RequestParam(name = "stateCode", required = false) @Size(max = 10)
            String stateCode) {

        return ResponseEntity.ok(constituencyService.getActiveConstituencies(stateCode));
    }
}
