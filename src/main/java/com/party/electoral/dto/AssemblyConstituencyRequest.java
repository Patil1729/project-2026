package com.party.electoral.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyConstituencyRequest {

    @NotBlank(message = "State code is required")
    @Size(max = 10, message = "State code cannot exceed 10 characters")
    private String stateCode;

    @NotBlank(message = "Constituency name is required")
    @Size(max = 255, message = "Constituency name cannot exceed 255 characters")
    private String name;

    @NotNull(message = "Constituency number is required")
    @Min(value = 1, message = "Constituency number must be greater than zero")
    private Integer constituencyNumber;
}
