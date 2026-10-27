package com.party.registration.admin.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.registration.application.entity.RegistrationStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter
@Setter
public class ApplicationSearchRequest {
    @Size(max = 150) private String q;
    private RegistrationStatus status;
    @Positive private Long applicantId;
    @Positive private Long committeeId;
    @Positive private Long organizationUnitId;
    private boolean includeDescendants = true;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate createdFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate createdTo;
    @Min(0) private int page = 0;
    @Min(1) @Max(100) private int size = 20;

    @JsonIgnore
    @AssertTrue(message = "createdFrom must be on or before createdTo")
    public boolean isDateRangeValid() {
        return createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo);
    }
}
