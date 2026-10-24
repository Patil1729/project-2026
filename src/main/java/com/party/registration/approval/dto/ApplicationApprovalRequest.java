package com.party.registration.approval.dto;

import jakarta.validation.constraints.Size;

public record ApplicationApprovalRequest(
        @Size(max = 2000)
        String comment) { }
