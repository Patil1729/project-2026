package com.party.registration.admin.search.service;

import com.party.common.dto.PageResponse;
import com.party.registration.admin.search.dto.*;

public interface AdminApplicationSearchService {
    PageResponse<AdminApplicationResponse> search(ApplicationSearchRequest request);
    AdminApplicationResponse getById(Long applicationId);
}
