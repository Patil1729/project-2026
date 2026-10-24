package com.party.registration.application.service;

import com.party.committee.entity.Committee;
import com.party.committee.entity.CommitteePosition;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.application.dto.RegistrationApplicationRequest;
import com.party.registration.application.dto.RegistrationApplicationResponse;
import com.party.registration.audit.entity.ApprovalAuditAction;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrationApplicationServiceImpl implements RegistrationApplicationService {

    private static final Set<RegistrationStatus> OPEN_STATUSES = Set.of(
            RegistrationStatus.AWAITING_RECOMMENDATION, RegistrationStatus.UNDER_REVIEW);

    private final RegistrationApplicationRepository applicationRepository;
    private final RegistrationApplicantRepository applicantRepository;
    private final CommitteeRepository committeeRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final CommitteePositionRepository positionRepository;
    private final ApprovalAuditLogRepository auditLogRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationApplicationResponse createApplication(
            String authenticatedUsername, RegistrationApplicationRequest request) {

        // Serializes simultaneous submissions by the same account. All creation
        // callers must use this service. Keep user -> committee -> unit(s) -> seat.
        AppUser applicant = applicantRepository.findByUsernameForUpdate(authenticatedUsername)
                .orElseThrow(this::unknownApplicant);
        requireEnabled(applicant);
        if (applicant.getProfile() == null) {
            throw failure(HttpStatus.CONFLICT, "PROFILE_REQUIRED",
                    "Complete your profile before creating a registration application.");
        }

        Long committeeId = positionRepository.findCommitteeIdByPositionId(request.getRequestedPositionId())
                .orElseThrow(() -> notFound("POSITION_NOT_FOUND", "Committee position not found."));
        Committee committee = committeeRepository.findByIdForUpdate(committeeId)
                .orElseThrow(() -> notFound("COMMITTEE_NOT_FOUND", "Committee not found."));

        OrganizationUnit committeeUnit = committee.getOrganizationUnit();
        if (committeeUnit == null || !Objects.equals(committeeUnit.getId(), request.getSelectedShakhaId())) {
            throw failure(HttpStatus.BAD_REQUEST, "POSITION_SHAKHA_MISMATCH",
                    "The requested position must belong to the selected Shakha's committee.");
        }

        // This preserves the relative write-lock order already used by the
        // committee-position service: committee -> organization(s) -> position.
        OrganizationUnit selectedShakha = lockOrganization(request.getSelectedShakhaId());
        Long responsibilityId = positionRepository
                .findResponsibilityUnitIdByPositionId(request.getRequestedPositionId()).orElse(null);
        OrganizationUnit responsibilityUnit = responsibilityId == null ? null : lockOrganization(responsibilityId);

        CommitteePosition position = positionRepository.findByIdForUpdate(request.getRequestedPositionId())
                .orElseThrow(() -> notFound("POSITION_NOT_FOUND", "Committee position not found."));

        if (position.getCommittee() == null || !Objects.equals(position.getCommittee().getId(), committeeId)) {
            throw failure(HttpStatus.CONFLICT, "POSITION_CHANGED", "The position changed. Reload and retry.");
        }
        if (!Boolean.TRUE.equals(committee.getActive()) || !position.isActive()) {
            throw failure(HttpStatus.CONFLICT, "INACTIVE_POSITION",
                    "The committee and requested position must both be active.");
        }
        requireActiveHierarchy(selectedShakha);
        if (responsibilityUnit != null) {
            requireActiveHierarchy(responsibilityUnit);
        }

        if (positionRepository.countActiveAssignmentsForPositionId(position.getId()) > 0) {
            throw failure(HttpStatus.CONFLICT, "POSITION_OCCUPIED",
                    "The requested position already has an active advocate appointment.");
        }
        if (applicationRepository.existsByApplicant_IdAndRequestedPosition_IdAndStatusIn(
                applicant.getId(), position.getId(), OPEN_STATUSES)) {
            throw failure(HttpStatus.CONFLICT, "DUPLICATE_OPEN_APPLICATION",
                    "You already have an open application for this position.");
        }

        RegistrationApplication application = RegistrationApplication.builder()
                .applicationNumber("REG-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT))
                .applicant(applicant)
                .selectedShakha(selectedShakha)
                .requestedPosition(position)
                .build();
        application = applicationRepository.save(application);

        // Both inserts commit or roll back together. There is no cascade from
        // RegistrationApplication to its auditLogs, so save the owning side.
        auditLogRepository.saveAndFlush(ApprovalAuditLog.builder()
                .application(application)
                .actor(applicant)
                .action(ApprovalAuditAction.REGISTRATION_CREATED)
                .eventKey(UUID.randomUUID().toString())
                .eventAt(Instant.now())
                .note("Application created for committee position " + position.getId())
                .build());

        return mapToResponse(application);
    }

    @Override
    public RegistrationApplicationResponse getMyApplication(String authenticatedUsername, Long id) {
        AppUser applicant = findEnabledApplicant(authenticatedUsername);
        RegistrationApplication application = applicationRepository.findByIdAndApplicant_Id(id, applicant.getId())
                .orElseThrow(() -> notFound("APPLICATION_NOT_FOUND", "Registration application not found."));
        return mapToResponse(application);
    }

    @Override
    public List<RegistrationApplicationResponse> getMyApplications(String authenticatedUsername) {
        AppUser applicant = findEnabledApplicant(authenticatedUsername);
        return applicationRepository.findAllByApplicant_IdOrderByIdDesc(applicant.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private AppUser findEnabledApplicant(String username) {
        AppUser applicant = applicantRepository.findByUsername(username).orElseThrow(this::unknownApplicant);
        requireEnabled(applicant);
        return applicant;
    }

    private void requireEnabled(AppUser applicant) {
        if (!applicant.isEnabled()) {
            throw failure(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Your account is disabled.");
        }
    }

    private OrganizationUnit lockOrganization(Long id) {
        return organizationUnitRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("ORGANIZATION_NOT_FOUND", "Organization unit not found."));
    }

    private void requireActiveHierarchy(OrganizationUnit unit) {
        Set<Long> visited = new HashSet<>();
        OrganizationUnit current = unit;
        while (current != null) {
            if (current.getId() == null || !visited.add(current.getId())
                    || !Boolean.TRUE.equals(current.getActive()) || current.getType() == null) {
                throw failure(HttpStatus.CONFLICT, "INVALID_ORGANIZATION",
                        "The organization hierarchy must be active and valid.");
            }
            OrganizationUnit parent = current.getParent();
            if (!current.getType().canHaveParent(parent == null ? null : parent.getType())) {
                throw failure(HttpStatus.CONFLICT, "INVALID_ORGANIZATION",
                        "The organization hierarchy is invalid.");
            }
            current = parent;
        }
    }

    private RegistrationApplicationResponse mapToResponse(RegistrationApplication application) {
        CommitteePosition position = application.getRequestedPosition();
        Committee committee = position.getCommittee();
        return RegistrationApplicationResponse.builder()
                .id(application.getId())
                .applicationNumber(application.getApplicationNumber())
                .applicantId(application.getApplicant().getId())
                .applicantName(application.getApplicant().getFullName())
                .selectedShakhaId(application.getSelectedShakha().getId())
                .selectedShakhaName(application.getSelectedShakha().getName())
                .committeeId(committee.getId())
                .committeeName(committee.getName())
                .requestedPositionId(position.getId())
                .positionType(position.getPositionType())
                .positionNumber(position.getPositionNumber())
                .status(application.getStatus())
                .createdAt(application.getCreatedAt())
                .submittedAt(application.getSubmittedAt())
                .decidedAt(application.getDecidedAt())
                .rejectionReason(application.getRejectionReason())
                .build();
    }

    private RegistrationException unknownApplicant() {
        return failure(HttpStatus.UNAUTHORIZED, "APPLICANT_NOT_AUTHENTICATED",
                "Sign in with an existing application account.");
    }

    private RegistrationException notFound(String code, String message) {
        return failure(HttpStatus.NOT_FOUND, code, message);
    }

    private RegistrationException failure(HttpStatus status, String code, String message) {
        return new RegistrationException(status, code, message);
    }
}
