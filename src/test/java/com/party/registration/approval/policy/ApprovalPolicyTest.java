package com.party.registration.approval.policy;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalPolicyTest {
    @Test
    void mondayAssignmentIsDueThursdayAtTheSameTime() {
        assertEquals(Instant.parse("2026-10-22T04:30:00Z"),
                ApprovalPolicy.deadlineFor(Instant.parse("2026-10-19T04:30:00Z")));
    }

    @Test
    void thursdayAssignmentSkipsTheWeekendAndIsDueTuesday() {
        assertEquals(Instant.parse("2026-10-27T04:30:00Z"),
                ApprovalPolicy.deadlineFor(Instant.parse("2026-10-22T04:30:00Z")));
    }

    @Test
    void fridayAssignmentIsDueWednesday() {
        assertEquals(Instant.parse("2026-10-28T04:30:00Z"),
                ApprovalPolicy.deadlineFor(Instant.parse("2026-10-23T04:30:00Z")));
    }

    @Test
    void saturdayAssignmentStartsMondayAndIsDueThursday() {
        assertEquals(Instant.parse("2026-10-29T04:30:00Z"),
                ApprovalPolicy.deadlineFor(Instant.parse("2026-10-24T04:30:00Z")));
    }

    @Test
    void sundayAssignmentStartsMondayAndIsDueThursday() {
        assertEquals(Instant.parse("2026-10-29T04:30:00Z"),
                ApprovalPolicy.deadlineFor(Instant.parse("2026-10-25T04:30:00Z")));
    }

    @Test
    void weekdayIsCalculatedInIndiaRatherThanUtc() {
        assertEquals(Instant.parse("2026-10-28T20:30:00Z"),
                ApprovalPolicy.deadlineFor(Instant.parse("2026-10-23T20:30:00Z")));
    }

    @Test
    void publicHolidaysAreOrdinaryWeekdaysInThisPolicy() {
        assertEquals(Instant.parse("2026-01-27T04:30:00Z"),
                ApprovalPolicy.deadlineFor(Instant.parse("2026-01-22T04:30:00Z")));
    }

    @Test
    void nullAssignmentTimeIsRejected() {
        assertThrows(NullPointerException.class, () -> ApprovalPolicy.deadlineFor(null));
    }

    @Test
    void missingDeadlineIsNotOverdue() {
        assertFalse(ApprovalPolicy.isOverdue(null, Instant.now()));
    }

    @Test
    void overdueBoundaryIncludesTheExactDeadline() {
        Instant due = Instant.parse("2026-10-22T04:30:00Z");
        assertFalse(ApprovalPolicy.isOverdue(due, due.minusNanos(1)));
        assertTrue(ApprovalPolicy.isOverdue(due, due));
        assertTrue(ApprovalPolicy.isOverdue(due, due.plusNanos(1)));
    }
}
