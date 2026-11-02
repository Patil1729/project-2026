package com.party.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank
    @Size(min = 3, max = 150)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
            message = "Username may contain letters, digits, dots, underscores and hyphens")
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;
}
