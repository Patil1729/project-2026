package com.party.registration.escalation.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalEscalationPropertiesTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    @AfterAll static void close() { FACTORY.close(); }

    @Test void defaultsAreValidAndEnableBoundedPolling() {
        var p = new ApprovalEscalationProperties();assertTrue(p.isEnabled());assertEquals(100, p.getBatchSize());
        assertEquals(60000, p.getPollIntervalMs());assertEquals(15000, p.getInitialDelayMs());assertTrue(VALIDATOR.validate(p).isEmpty());
    }
    @Test void batchCannotBeEmpty() { var p = new ApprovalEscalationProperties();p.setBatchSize(0);assertFalse(VALIDATOR.validate(p).isEmpty()); }
    @Test void batchCannotBeUnbounded() { var p = new ApprovalEscalationProperties();p.setBatchSize(1001);assertFalse(VALIDATOR.validate(p).isEmpty()); }
    @Test void pollingMustBeAtLeastOneSecond() { var p = new ApprovalEscalationProperties();p.setPollIntervalMs(999);assertFalse(VALIDATOR.validate(p).isEmpty()); }
    @Test void initialDelayCannotBeNegative() { var p = new ApprovalEscalationProperties();p.setInitialDelayMs(-1);assertFalse(VALIDATOR.validate(p).isEmpty()); }
    @Test void disabledJobStillAcceptsValidBoundarySettings() {
        var p = new ApprovalEscalationProperties();p.setEnabled(false);p.setBatchSize(1000);p.setPollIntervalMs(1000);p.setInitialDelayMs(0);
        assertTrue(VALIDATOR.validate(p).isEmpty());
    }
}
