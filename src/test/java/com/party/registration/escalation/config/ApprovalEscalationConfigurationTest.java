package com.party.registration.escalation.config;

import com.party.registration.escalation.scheduler.ApprovalEscalationScheduler;
import com.party.registration.escalation.service.ApprovalEscalationBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalEscalationConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ApprovalEscalationConfiguration.class, ApprovalEscalationScheduler.class)
            .withBean(ApprovalEscalationBatchService.class, () -> mock(ApprovalEscalationBatchService.class))
            .withPropertyValues("app.registration.approval.escalation.initial-delay-ms=3600000");

    @Test void enabledPropertyRegistersSchedulerAndProvidesNamedUtcClock() {
        context.withPropertyValues("app.registration.approval.escalation.enabled=true").run(c -> {
            assertNull(c.getStartupFailure());assertNotNull(c.getBean(ApprovalEscalationScheduler.class));
            assertEquals(ZoneOffset.UTC, c.getBean(ApprovalEscalationConfiguration.CLOCK_BEAN, Clock.class).getZone());
        });
    }
    @Test void disabledPropertyRemovesTheScheduledBean() {
        context.withPropertyValues("app.registration.approval.escalation.enabled=false").run(c -> {
            assertNull(c.getStartupFailure());assertTrue(c.getBeansOfType(ApprovalEscalationScheduler.class).isEmpty());
            assertNotNull(c.getBean(ApprovalEscalationProperties.class));
        });
    }
}
