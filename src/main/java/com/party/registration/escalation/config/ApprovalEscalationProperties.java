package com.party.registration.escalation.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.registration.approval.escalation")
public class ApprovalEscalationProperties {
    private boolean enabled = true;
    @Min(1) @Max(1000)
    private int batchSize = 100;
    @Min(1000)
    private long pollIntervalMs = 60000;
    @Min(0)
    private long initialDelayMs = 15000;
}
