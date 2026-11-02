package com.party.administration.access.controller;

import com.party.administration.access.dto.AreaAccessResponse;
import com.party.administration.access.service.AreaAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/auth")
public class AreaAccessController {
    private final AreaAccessService access;
    @GetMapping("/access")
    public ResponseEntity<AreaAccessResponse> access() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.currentAccess());
    }
}
