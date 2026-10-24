package com.party.registration.application.controller;

import com.party.registration.application.dto.RegistrationApplicationRequest;
import com.party.registration.application.dto.RegistrationApplicationResponse;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.application.service.RegistrationApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/registration-applications")
@RequiredArgsConstructor
public class RegistrationApplicationController {

    private final RegistrationApplicationService applicationService;
    private final Environment environment;

    @PostMapping
    public ResponseEntity<RegistrationApplicationResponse> createApplication(
            Principal principal, @Valid @RequestBody RegistrationApplicationRequest request) {
        RegistrationApplicationResponse response = applicationService.createApplication(username(principal), request);
        return ResponseEntity.created(URI.create("/api/registration-applications/" + response.getId())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegistrationApplicationResponse> getMyApplication(
            Principal principal, @PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(applicationService.getMyApplication(username(principal), id));
    }

    @GetMapping("/my")
    public ResponseEntity<List<RegistrationApplicationResponse>> getMyApplications(Principal principal) {
        return ResponseEntity.ok(applicationService.getMyApplications(username(principal)));
    }

    private String username(Principal principal) {
        // Explicit local-only testing while authentication is not implemented.
        // An authenticated principal always takes precedence over this account.
        if (principal == null && environment.acceptsProfiles(Profiles.of("local"))) {
            String localUsername = environment.getProperty("app.registration.local-test-username");
            if (localUsername != null && !localUsername.isBlank()) {
                return localUsername.strip();
            }
        }
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new RegistrationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Sign in before accessing registration applications.");
        }
        return principal.getName();
    }
}
