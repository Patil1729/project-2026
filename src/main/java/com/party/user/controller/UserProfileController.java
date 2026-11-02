package com.party.user.controller;

import com.party.user.dto.UserProfileRequest;
import com.party.user.dto.UserProfileResponse;
import com.party.user.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/users/{userId}/profile")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PARTY_HEAD') or #userId == principal.id")
public class UserProfileController {

    private final UserProfileService profileService;

    @PostMapping
    public ResponseEntity<UserProfileResponse> createProfile(
            @PathVariable("userId") @Positive Long userId,
            @Valid @RequestBody UserProfileRequest request) {

        UserProfileResponse response = profileService.createProfile(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PARTY_HEAD') or #userId == principal.id or @areaAccess.canReadUser(#userId)")
    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(
            @PathVariable("userId") @Positive Long userId) {

        return ResponseEntity.ok(profileService.getProfileByUserId(userId));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            @PathVariable("userId") @Positive Long userId,
            @Valid @RequestBody UserProfileRequest request) {

        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }
}
