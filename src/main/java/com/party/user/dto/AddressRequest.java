package com.party.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

/** Use as @NotNull @Valid AddressRequest address in the registration/profile DTO. */
public record AddressRequest(
        @Size(max = 50) String houseOrFlatNumber,
        @Size(max = 150) String buildingName,
        @Size(max = 200) String street,
        @Size(max = 150) String area,
        @Size(max = 200) String landmark,
        @Size(max = 150) String village,
        @Size(max = 150) String city,
        @NotBlank @Size(max = 150) String taluka,
        @NotBlank @Size(max = 150) String district,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 100) String country,
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{5}", message = "PIN code must contain six digits")
        String pinCode
) {
    public AddressRequest {
        // Missing country receives the same default as Address.
        if (country == null) {
            country = "India";
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Provide a city or village")
    public boolean isSettlementProvided() {
        return (city != null && !city.isBlank()) || (village != null && !village.isBlank());
    }
}
