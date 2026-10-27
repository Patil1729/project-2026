package com.party.registration.audit.service;

import com.party.common.dto.PageResponse;
import com.party.registration.audit.dto.ApplicationAuditResponse;

public interface ApplicationAuditService {
    PageResponse<ApplicationAuditResponse> getMyHistory(String username, Long applicationId, int page, int size);
    PageResponse<ApplicationAuditResponse> getAdminHistory(Long applicationId, int page, int size);
}
