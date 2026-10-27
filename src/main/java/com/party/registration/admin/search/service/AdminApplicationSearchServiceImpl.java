package com.party.registration.admin.search.service;

import com.party.common.dto.PageResponse;
import com.party.registration.admin.common.service.AdminOrganizationScopeService;
import com.party.registration.admin.search.dto.*;
import com.party.registration.admin.search.repository.*;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.common.exception.RegistrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminApplicationSearchServiceImpl implements AdminApplicationSearchService {
    private final AdminApplicationRepository repository;
    private final AdminOrganizationScopeService scopes;

    public PageResponse<AdminApplicationResponse> search(ApplicationSearchRequest request) {
        if (!request.isDateRangeValid()) throw new RegistrationException(HttpStatus.BAD_REQUEST,
                "INVALID_DATE_RANGE", "createdFrom must be on or before createdTo.");
        if (request.getPage() < 0 || request.getSize() < 1 || request.getSize() > 100)
            throw new RegistrationException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Use page >= 0 and size between 1 and 100.");
        var scope = scopes.resolve(request.getOrganizationUnitId(), request.isIncludeDescendants());
        var page = PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(repository.findAll(ApplicationSearchSpecifications.matching(request, scope), page).map(this::toResponse));
    }

    public AdminApplicationResponse getById(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() -> new RegistrationException(
                HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Registration application " + id + " not found.")));
    }

    private AdminApplicationResponse toResponse(RegistrationApplication app) {
        var user = app.getApplicant();
        var position = app.getRequestedPosition();
        var committee = position.getCommittee();
        return new AdminApplicationResponse(app.getId(), app.getApplicationNumber(), user.getId(),
                user.getUsername(), user.getFullName(), user.getMobile(), app.getSelectedShakha().getId(),
                app.getSelectedShakha().getName(), committee.getId(), committee.getName(), position.getId(),
                position.getPositionType(), position.getPositionNumber(), app.getStatus(), app.getCreatedAt(),
                app.getSubmittedAt(), app.getDecidedAt(), app.getRejectionReason(),
                app.getAssignment() == null ? null : app.getAssignment().getId());
    }
}
