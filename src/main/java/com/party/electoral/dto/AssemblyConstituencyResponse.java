package com.party.electoral.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyConstituencyResponse {

    private Long id;
    private String stateCode;
    private String name;
    private Integer constituencyNumber;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
