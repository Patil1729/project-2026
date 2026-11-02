package com.party.user.controller;

import com.party.user.dto.UserRequest;
import com.party.user.dto.UserResponse;
import com.party.user.dto.UserUpdateRequest;
import com.party.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PARTY_HEAD')")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody UserRequest request) {

        UserResponse response = userService.createUser(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PARTY_HEAD')")
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable("userId")
            @Positive(message = "User ID must be greater than zero")
            Long userId,
            @Valid @RequestBody UserUpdateRequest request) {

        return ResponseEntity.ok(
                userService.updateUser(userId, request)
        );
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PARTY_HEAD')")
    @PatchMapping("/{userId}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable("userId")
            @Positive(message = "User ID must be greater than zero")
            Long userId) {

        return ResponseEntity.ok(
                userService.deactivateUser(userId)
        );
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PARTY_HEAD') or #userId == principal.id or @areaAccess.canReadUser(#userId)")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable("userId")
            @Positive(message = "User ID must be greater than zero")
            Long userId) {

        return ResponseEntity.ok(
                userService.getUserById(userId)
        );
    }

    @org.springframework.security.access.prepost.PreAuthorize("@areaAccess.hasAdminAccess()")
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {

        return ResponseEntity.ok(
                userService.getAllUsers()
        );
    }
}