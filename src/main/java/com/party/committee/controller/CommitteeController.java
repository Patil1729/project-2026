package com.party.committee.controller;

import com.party.committee.dto.CommitteeRequest;
import com.party.committee.dto.CommitteeResponse;
import com.party.committee.service.CommitteeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

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

}
