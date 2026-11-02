package com.party.security.password.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record ChangePasswordRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 72) String currentPassword,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(min = 8, max = 72) String newPassword,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 72) String confirmPassword) {
    @JsonIgnore
    @AssertTrue(message = "New password must not exceed 72 UTF-8 bytes")
    public boolean isWithinBcryptLimit() {
        return (newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72)
                && (currentPassword == null || currentPassword.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
    @JsonIgnore
    @AssertTrue(message = "New password and confirmation must match")
    public boolean isConfirmationMatching() {
        return newPassword == null || newPassword.equals(confirmPassword);
    }

    @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
}
