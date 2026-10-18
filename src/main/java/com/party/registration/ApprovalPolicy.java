package com.party.registration;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/** The agreed three-day window is 72 elapsed hours, including weekends. */
public final class ApprovalPolicy {

    public static final Duration REVIEW_WINDOW = Duration.ofHours(72);

    private ApprovalPolicy() {
    }

    public static Instant deadlineFor(Instant assignedAt) {
        return Objects.requireNonNull(assignedAt, "assignedAt is required").plus(REVIEW_WINDOW);
    }

    public static boolean isOverdue(Instant dueAt, Instant now) {
        return dueAt != null && !Objects.requireNonNull(now, "now is required").isBefore(dueAt);
    }
}
