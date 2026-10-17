package com.party.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class UserRequest {

    @NotBlank
    @Size(min = 3, max = 150)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
            message = "Username may contain letters, digits, dots, underscores and hyphens")
    private String username;

    // Never use @Data here: its generated toString() could expose the password.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    @Size(max = 150)
    private String fullName;

    @NotBlank
    @Pattern(regexp = "\\+[1-9][0-9]{7,14}",
            message = "Use + followed by country code and mobile number, for example +919876543210")
    private String mobile;

    @Email
    @Size(max = 255)
    private String email;

    @Positive
    private Long organizationUnitId;

    @JsonIgnore
    @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

}
