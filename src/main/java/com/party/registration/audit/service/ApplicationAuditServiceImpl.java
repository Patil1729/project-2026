package com.party.registration.audit.service;

import com.party.common.dto.PageResponse;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.registration.audit.dto.ApplicationAuditResponse;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationAuditServiceImpl implements ApplicationAuditService {
    private final ApprovalAuditLogRepository audits;
    private final RegistrationApplicationRepository applications;
    private final RegistrationApplicantRepository users;

    public PageResponse<ApplicationAuditResponse> getMyHistory(String username, Long applicationId, int page, int size) {
        var user = users.findByUsername(username).orElseThrow(() -> new RegistrationException(
                HttpStatus.UNAUTHORIZED, "APPLICANT_NOT_AUTHENTICATED", "The username has no applicant account."));
        if (!user.isEnabled()) throw new RegistrationException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Your account is disabled.");
        applications.findByIdAndApplicant_Id(applicationId, user.getId()).orElseThrow(this::notFound);
        return history(applicationId, page, size);
    }

    public PageResponse<ApplicationAuditResponse> getAdminHistory(Long applicationId, int page, int size) {
        if (!applications.existsById(applicationId)) throw notFound();
        return history(applicationId, page, size);
    }

    private PageResponse<ApplicationAuditResponse> history(Long applicationId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new RegistrationException(HttpStatus.BAD_REQUEST,
                "INVALID_PAGE", "Use page >= 0 and size between 1 and 100.");
        return PageResponse.from(audits.findByApplication_IdOrderByEventAtAscIdAsc(applicationId, PageRequest.of(page, size))
                .map(event -> new ApplicationAuditResponse(event.getId(), applicationId,
                        event.getApprovalStep() == null ? null : event.getApprovalStep().getId(), event.getAction(),
                        event.getActor() == null ? null : event.getActor().getId(),
                        event.getActor() == null ? null : event.getActor().getUsername(), event.getEventAt(), event.getNote())));
    }

    private RegistrationException notFound() {
        return new RegistrationException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Registration application not found.");
    }
}
