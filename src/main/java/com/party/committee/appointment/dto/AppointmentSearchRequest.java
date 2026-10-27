package com.party.committee.appointment.dto;

import com.party.committee.entity.CommitteePositionType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentSearchRequest {
    @Positive private Long committeeId;
    @Positive private Long advocateUserId;
    private CommitteePositionType positionType;
    private Boolean active;
    @Min(0) private int page = 0;
    @Min(1) @Max(100) private int size = 20;
}
