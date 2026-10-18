package com.party.user.dto;

import java.time.LocalDateTime;


public record AddressResponse(
        Long id,
        String houseOrFlatNumber,
        String buildingName,
        String street,
        String area,
        String landmark,
        String village,
        String city,
        String taluka,
        String district,
        String state,
        String country,
        String pinCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
