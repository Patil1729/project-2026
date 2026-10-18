package com.party.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UserProfileRequest {

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Education is required")
    @Size(max = 255, message = "Education cannot exceed 255 characters")
    private String education;

    @NotNull(message = "Address is required")
    @Valid
    private AddressRequest address;

    @NotNull(message = "Assembly constituency ID is required")
    @Positive(message = "Assembly constituency ID must be greater than zero")
    private Long assemblyConstituencyId;

    @NotBlank(message = "Voter ID number is required")
    @Size(max = 30, message = "Voter ID number cannot exceed 30 characters")
    private String voterIdNumber;

    @NotNull(message = "Voter list part number is required")
    @Min(value = 1, message = "Voter list part number must be at least 1")
    private Integer voterListPartNumber;

    @Pattern(regexp = "[0-9]{4}",
            message = "Aadhaar display reference must contain exactly four digits")
    private String aadhaarLastFour;
}
