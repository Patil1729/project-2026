package com.party.portal.controller;

import com.party.portal.dto.PublicOrganizationUnit;
import com.party.portal.dto.StateExecutiveSeat;
import com.party.portal.service.PortalCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/portal")
public class PortalCatalogController {
    private final PortalCatalogService service;

    @GetMapping("/organization-units")
    public ResponseEntity<List<PublicOrganizationUnit>> organizations() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.organizations());
    }

    @GetMapping("/state-executive/positions")
    public ResponseEntity<List<StateExecutiveSeat>> statePositions() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.statePositions());
    }
}
