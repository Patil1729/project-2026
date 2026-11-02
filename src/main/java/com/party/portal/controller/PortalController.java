package com.party.portal.controller;

import com.party.portal.dto.PortalSummary;
import com.party.portal.dto.StateExecutiveMember;
import com.party.portal.service.PortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/public/portal")
@RequiredArgsConstructor
public class PortalController {
    private final PortalService service;

    @GetMapping("/summary")
    public ResponseEntity<PortalSummary> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary());
    }

    @GetMapping("/state-executive")
    public ResponseEntity<List<StateExecutiveMember>> stateExecutive() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.stateExecutive());
    }
}
