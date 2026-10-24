package com.party.registration.approval.policy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** Three working days in India; Saturday/Sunday are skipped, public holidays count. */
public final class ApprovalPolicy {

    public static final int REVIEW_WORKING_DAYS = 3;
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private ApprovalPolicy() {
    }

    public static Instant deadlineFor(Instant assignedAt) {
        var cursor = Objects.requireNonNull(assignedAt, "assignedAt is required").atZone(BUSINESS_ZONE);
        // A weekend assignment starts its clock on Monday at the same local time.
        while (isWeekend(cursor.getDayOfWeek())) {
            cursor = cursor.plusDays(1);
        }
        int remaining = REVIEW_WORKING_DAYS;
        while (remaining > 0) {
            cursor = cursor.plusDays(1);
            if (!isWeekend(cursor.getDayOfWeek())) {
                remaining--;
            }
        }
        return cursor.toInstant();
    }

    public static boolean isOverdue(Instant dueAt, Instant now) {
        return dueAt != null && !Objects.requireNonNull(now, "now is required").isBefore(dueAt);
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
