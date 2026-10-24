package com.party.registration.escalation.service;

import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.escalation.config.ApprovalEscalationProperties;
import com.party.registration.escalation.dto.ApprovalEscalationCandidate;
import com.party.registration.escalation.dto.ApprovalEscalationOutcome;
import com.party.registration.approval.repository.ApprovalStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalEscalationBatchServiceTest {
    @Mock ApprovalStepRepository steps;
    @Mock ApprovalEscalationService worker;
    private ApprovalEscalationBatchService batch;
    private final Instant now = Instant.parse("2026-10-20T09:00:00Z");
    private final ApprovalEscalationCandidate first = new ApprovalEscalationCandidate(1L, 10L);
    private final ApprovalEscalationCandidate second = new ApprovalEscalationCandidate(2L, 20L);

    @BeforeEach
    void setUp() {
        var properties = new ApprovalEscalationProperties();properties.setBatchSize(2);
        batch = new ApprovalEscalationBatchService(steps, worker, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void boundedBatchUsesOneClockAndReportsIndependentOutcomes() {
        candidates(0, List.of(first, second));
        when(worker.processCandidate(first, now)).thenReturn(new ApprovalEscalationOutcome(true, true, null, false, false));
        when(worker.processCandidate(second, now)).thenReturn(new ApprovalEscalationOutcome(true, false, "HEAD_MISSING", true, false));
        var result = batch.processBatch();
        assertEquals(2, result.scanned());assertEquals(2, result.overdueMarked());
        assertEquals(1, result.escalated());assertEquals(1, result.unresolved());assertEquals(2, result.lastSeenStepId());
        verify(steps).findEscalationCandidates(eq(now), eq(0L), eq(RegistrationStatus.UNDER_REVIEW), anyList(), eq(PageRequest.of(0, 2)));
    }

    @Test
    void oneCandidateFailureDoesNotPreventAnotherFromCommitting() {
        candidates(0, List.of(first, second));
        when(worker.processCandidate(first, now)).thenThrow(new IllegalStateException("temporary failure"));
        when(worker.processCandidate(second, now)).thenReturn(new ApprovalEscalationOutcome(true, true, null, false, false));
        var result = batch.processBatch();
        assertEquals(1, result.failed());assertEquals(1, result.escalated());assertEquals(2, result.lastSeenStepId());
        verify(worker).processCandidate(second, now);
    }

    @Test
    void cursorContinuesPastUnresolvedOldRowsOnTheNextTick() {
        candidates(0, List.of(first, second));
        var third = new ApprovalEscalationCandidate(3L, 30L);
        candidates(2, List.of(third));
        when(worker.processCandidate(any(), eq(now))).thenReturn(new ApprovalEscalationOutcome(false, false, "HEAD_MISSING", false, false));
        batch.processBatch();
        assertEquals(3, batch.processBatch().lastSeenStepId());
        verify(worker).processCandidate(third, now);
    }

    @Test
    void endOfScanWrapsAndRetriesMissingSenior() {
        candidates(0, List.of(first));candidates(1, List.of());
        when(worker.processCandidate(first, now)).thenReturn(new ApprovalEscalationOutcome(false, false, "HEAD_MISSING", false, false));
        batch.processBatch();batch.processBatch();
        verify(worker, times(2)).processCandidate(first, now);
        verify(steps, times(2)).findEscalationCandidates(eq(now), eq(0L), any(), anyList(), any());
    }

    @Test
    void emptyQueueDoesNotInvokeWorker() {
        candidates(0, List.of());
        assertEquals(0, batch.processBatch().scanned());
        verifyNoInteractions(worker);
    }

    @Test
    void staleCandidatesAreReportedAsSkipped() {
        candidates(0, List.of(first));when(worker.processCandidate(first, now)).thenReturn(ApprovalEscalationOutcome.skip());
        assertEquals(1, batch.processBatch().skipped());
    }

    @Test
    void queueLoadFailurePropagatesWithoutCallingWorker() {
        when(steps.findEscalationCandidates(any(), anyLong(), any(), anyList(), any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, batch::processBatch);verifyNoInteractions(worker);
    }

    private void candidates(long after, List<ApprovalEscalationCandidate> values) {
        when(steps.findEscalationCandidates(eq(now), eq(after), any(), anyList(), any())).thenReturn(values);
    }
}
