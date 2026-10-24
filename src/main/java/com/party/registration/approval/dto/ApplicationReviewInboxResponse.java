package com.party.registration.approval.dto;

import java.util.List;

public record ApplicationReviewInboxResponse(
        List<ApplicationReviewInboxItem> items,
        int page,
        int size,
        boolean hasNext) {
}
