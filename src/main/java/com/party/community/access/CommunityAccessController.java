package com.party.community.access;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class CommunityAccessController {
    private final CommunityAccessService access;
    @GetMapping("/api/community/access") public ResponseEntity<CommunityAccessService.Capabilities> access() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.capabilities());
    }
}
