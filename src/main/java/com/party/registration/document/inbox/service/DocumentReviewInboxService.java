package com.party.registration.document.inbox.service;

import com.party.administration.access.policy.AreaScopePolicy;
import com.party.administration.access.repository.AreaAccessRepository;
import com.party.administration.access.service.AreaAccessService;
import com.party.committee.entity.CommitteePositionType;
import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.common.dto.PageResponse;
import com.party.registration.application.dto.RegistrationApplicationResponse;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.admin.search.repository.AdminApplicationRepository;
import com.party.registration.common.exception.RegistrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class DocumentReviewInboxService {
    private final AdvocateAssignmentRepository appointments;
    private final AreaAccessRepository hierarchy;
    private final AdminApplicationRepository applications;
    private final AreaAccessService access;
    private final Clock clock;

    public PageResponse<RegistrationApplicationResponse> inbox(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new RegistrationException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Use page >= 0 and size between 1 and 100.");
        Long actor = access.actorId(); var units = presidencyUnits(actor);
        if (units.isEmpty()) return new PageResponse<>(List.of(), page, size, 0, 0, false);
        Specification<RegistrationApplication> filter = (root, query, cb) -> cb.and(
                root.get("selectedShakha").get("id").in(units),
                cb.equal(root.get("status"), RegistrationStatus.AWAITING_RECOMMENDATION),
                cb.notEqual(root.get("applicant").get("id"), actor));
        return PageResponse.from(applications.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(this::response));
    }
    public RegistrationApplicationResponse get(Long id) {
        Long actor = access.actorId();
        var app = applications.findById(id).orElseThrow(() -> new RegistrationException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Application not found."));
        if (!presidencyUnits(actor).contains(app.getSelectedShakha().getId()) && !access.canAccessUnit(app.getSelectedShakha().getId()))
            throw new RegistrationException(HttpStatus.FORBIDDEN, "DOCUMENT_REVIEW_FORBIDDEN", "A current presidency or an administration assignment for this area is required to view these documents.");
        return response(app);
    }
    private Set<Long> presidencyUnits(Long userId) {
        var nodes = hierarchy.hierarchy(); Set<Long> units = new HashSet<>();
        for (var appointment : appointments.findCurrentPresidenciesForUser(userId, CommitteePositionType.PRESIDENT, LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))))) {
            var root = appointment.getCommitteePosition().getCommittee().getOrganizationUnit();
            var role = AreaScopePolicy.ROLE_TYPES.entrySet().stream().filter(e -> e.getValue() == root.getType()).map(Map.Entry::getKey).findFirst().orElse(null);
            if (role != null && AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(role, root.getId()), nodes)) {
                try { units.addAll(AreaScopePolicy.descendants(root.getId(), nodes)); }
                catch (IllegalArgumentException invalid) { throw new RegistrationException(HttpStatus.CONFLICT, "INVALID_ORGANIZATION", "Repair the organisation hierarchy before reviewing documents."); }
            }
        }
        // A paused branch does not offer new document-verification tasks.
        var active = new HashSet<Long>();
        for (var node : nodes) {
            var role = AreaScopePolicy.ROLE_TYPES.entrySet().stream().filter(e -> e.getValue() == node.type()).map(Map.Entry::getKey).findFirst().orElse(null);
            if (role != null && AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(role, node.id()), nodes)) active.add(node.id());
        }
        units.retainAll(active); return units;
    }
    private RegistrationApplicationResponse response(RegistrationApplication app) {
        var position = app.getRequestedPosition(); var committee = position.getCommittee();
        return RegistrationApplicationResponse.builder().id(app.getId()).applicationNumber(app.getApplicationNumber())
                .applicantId(app.getApplicant().getId()).applicantName(app.getApplicant().getFullName())
                .selectedShakhaId(app.getSelectedShakha().getId()).selectedShakhaName(app.getSelectedShakha().getName())
                .committeeId(committee.getId()).committeeName(committee.getName()).requestedPositionId(position.getId())
                .positionType(position.getPositionType()).positionNumber(position.getPositionNumber()).status(app.getStatus())
                .createdAt(app.getCreatedAt()).submittedAt(app.getSubmittedAt()).decidedAt(app.getDecidedAt()).rejectionReason(app.getRejectionReason()).build();
    }
}
