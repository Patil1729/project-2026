package com.party.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {

    private Long id;
    private Long userId;
    private String fullName;

    private LocalDate dateOfBirth;
    private Integer age;
    private String education;

    private AddressResponse address;

    private Long assemblyConstituencyId;
    private String assemblyConstituencyName;
    private Integer assemblyConstituencyNumber;

    private String voterIdNumber;
    private Integer voterListPartNumber;
    private String aadhaarLastFour;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
