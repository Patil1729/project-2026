package com.party.user.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {

    @NotBlank
    @Size(min = 3, max = 150)
    @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
            message = "Username must start with a letter or digit and may contain dots, underscores and hyphens"
    )
    private String username;

    @NotBlank
    @Size(max = 150)
    private String fullName;

    @NotBlank
    @Pattern(
            regexp = "\\+[1-9][0-9]{7,14}",
            message = "Use country code and mobile number, for example +919876543210"
    )
    private String mobile;

    @Email
    @Size(max = 255)
    private String email;

    @Positive
    private Long organizationUnitId;
}