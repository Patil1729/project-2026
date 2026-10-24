package com.party.registration.escalation.scheduler;

import com.party.registration.escalation.dto.ApprovalEscalationBatchResult;
import com.party.registration.escalation.service.ApprovalEscalationBatchService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalEscalationSchedulerTest {
    @Test
    void scheduledCallDelegatesOneBatch() {
        var batch = mock(ApprovalEscalationBatchService.class);
        when(batch.processBatch()).thenReturn(new ApprovalEscalationBatchResult(2, 1, 1, 0, 1, 0, 2));
        new ApprovalEscalationScheduler(batch).scan();verify(batch, times(1)).processBatch();
    }

    @Test
    void loadingFailureDoesNotEscapeTheScheduledMethod() {
        var batch = mock(ApprovalEscalationBatchService.class);
        when(batch.processBatch()).thenThrow(new IllegalStateException("database unavailable"));
        assertDoesNotThrow(() -> new ApprovalEscalationScheduler(batch).scan());
    }
}
