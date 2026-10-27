package com.party.registration.approval.reassignment.service;

import com.party.registration.approval.reassignment.dto.*;

public interface ReviewerReassignmentService {
    ReviewerReassignmentResponse reassign(Long applicationId, ReviewerReassignmentRequest request);
}
