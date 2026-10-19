package com.party.registration.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationDocumentVerificationRequest {
    @Size(max = 2000)
    private String comment;
}
