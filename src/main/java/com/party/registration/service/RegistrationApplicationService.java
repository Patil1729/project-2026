package com.party.registration.service;

import com.party.registration.dto.RegistrationApplicationRequest;
import com.party.registration.dto.RegistrationApplicationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public interface RegistrationApplicationService {

    RegistrationApplicationResponse createApplication(
            @NotBlank String authenticatedUsername,
            @NotNull @Valid RegistrationApplicationRequest request);

    RegistrationApplicationResponse getMyApplication(
            @NotBlank String authenticatedUsername, @NotNull @Positive Long id);

    List<RegistrationApplicationResponse> getMyApplications(@NotBlank String authenticatedUsername);
}
