package com.party.committee.appointment.service;

import com.party.common.dto.PageResponse;
import com.party.committee.appointment.dto.*;

public interface AppointmentService {
    AppointmentResponse bootstrapStatePresident(BootstrapPresidentRequest request);
    AppointmentResponse getById(Long assignmentId);
    PageResponse<AppointmentResponse> search(AppointmentSearchRequest request);
    AppointmentResponse end(Long assignmentId, EndAppointmentRequest request);
    AppointmentReplacementResponse replace(Long assignmentId, ReplaceAppointmentRequest request);
    PageResponse<AppointmentAuditResponse> history(Long assignmentId, int page, int size);
}
