package com.party.committee.service;


import com.party.committee.dto.CommitteePositionResponse;
import com.party.committee.entity.*;
import com.party.committee.exception.CommitteeNotFoundException;
import com.party.committee.exception.CommitteePositionInUseException;
import com.party.committee.exception.CommitteePositionNotFoundException;
import com.party.committee.exception.InvalidCommitteePositionException;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.exception.OrganizationUnitNotFoundException;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.entity.RegistrationStatus;
import com.party.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class CommitteePositionServiceImpl implements CommitteePositionService {

    private final CommitteePositionRepository committeePositionRepository;
    private final CommitteeRepository committeeRepository;
    private final OrganizationUnitRepository organizationUnitRepository;

    private static final List<RegistrationStatus> PENDING_STATUSES = List.of(
            RegistrationStatus.AWAITING_RECOMMENDATION,
            RegistrationStatus.UNDER_REVIEW);


    @Override
    @Transactional(readOnly = true)
    public List<CommitteePositionResponse> getPositionsByCommitteeId(Long committeeId) {
        validateId(committeeId, "Committee id");
        //1. check Exist or not
        committeeRepository.findById(committeeId)
                .orElseThrow(() -> new CommitteeNotFoundException(
                        "Committee with id " + committeeId + " not found"));
        return toResponses(committeePositionRepository.findPositionsByCommitteeId(committeeId));
    }

    @Override
    @Transactional(readOnly = true)
    public CommitteePositionResponse getPositionById(Long id) {
        validateId(id, "Position id");
        CommitteePosition position = committeePositionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new CommitteePositionNotFoundException("Committee position not found with id: " + id));
        return toResponses(List.of(position)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommitteePositionResponse> getVacantPositionsByCommitteeId(Long committeeId) {
        // available excludes inactive seats, inactive committees/hierarchies and occupied seats.
        // Vacant positions shown to an applicant must also be usable.
        return getPositionsByCommitteeId(committeeId).stream()
                .filter(CommitteePositionResponse::available)
                .toList();
    }

    @Override
    public CommitteePositionResponse activatePosition(Long id) {
        CommitteePosition position = findPositionForUpdate(id);
        String reason = getUnavailabilityReason(position);
        if (reason != null) {
            throw new InvalidCommitteePositionException("Cannot activate position: " + reason);
        }

        if (!position.isActive()) {
            position.setActive(true);
            committeePositionRepository.saveAndFlush(position);
        }
        return toResponses(List.of(position)).get(0);
    }

    @Override
    public CommitteePositionResponse deactivatePosition(Long id) {
        CommitteePosition position = findPositionForUpdate(id);

        if (!position.isActive()) {
            return toResponses(List.of(position)).get(0);
        }

        if (committeePositionRepository.countActiveAssignmentsForPositionId(id) > 0) {
            throw new CommitteePositionInUseException(
                    "Cannot deactivate position: an active advocate appointment exists. "
                            + "End the appointment first.");
        }

        if (committeePositionRepository.countApplicationsForPositionIdAndStatuses(
                id, PENDING_STATUSES) > 0) {
            throw new CommitteePositionInUseException(
                    "Cannot deactivate position: pending registration applications exist. "
                            + "Resolve those applications first.");
        }

        position.setActive(false);
        committeePositionRepository.saveAndFlush(position);
        return toResponses(List.of(position)).get(0);
    }

    private void validateId(Long id, String label) {
        if (id == null || id <= 0) {
            throw new InvalidCommitteePositionException(label + " must be a positive number");
        }
    }

    private CommitteePosition findPositionForUpdate(Long id) {
        validateId(id, "Position id");

        Long committeeId = committeePositionRepository.findCommitteeIdByPositionId(id)
                .orElseThrow(() -> new CommitteePositionNotFoundException("Committee position not found with id: " + id));

        // Existing-committee writes lock committee -> organization(s) -> position.
        Committee committee = committeeRepository.findByIdForUpdate(committeeId)
                .orElseThrow(() -> new CommitteeNotFoundException(
                        "Committee with id " + committeeId + " not found"));

        lockOrganization(committee.getOrganizationUnit().getId());
        committeePositionRepository.findResponsibilityUnitIdByPositionId(id)
                .ifPresent(this::lockOrganization);

        return committeePositionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CommitteePositionNotFoundException("Committee position not found with id: " + id));
    }

    private void lockOrganization(Long id) {
        organizationUnitRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + id + " not found"));
    }

    private List<CommitteePositionResponse> toResponses(List<CommitteePosition> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }

        List<Long> ids = positions.stream().map(CommitteePosition::getId).toList();
        Map<Long, AdvocateAssignment> activeAssignments = new HashMap<>();

        for (AdvocateAssignment assignment
                : committeePositionRepository.findActiveAssignmentsForPositions(ids)) {

            Long positionId = assignment.getCommitteePosition().getId();
            if (activeAssignments.putIfAbsent(positionId, assignment) != null) {
                throw new IllegalStateException(
                        "More than one active advocate appointment exists for position " + positionId);
            }

        }

        // Keep the enum/template order, rather than alphabetical database enum names.
        Comparator<CommitteePosition> order = Comparator
                .comparing(CommitteePosition::getPositionType,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CommitteePosition::getPositionNumber,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CommitteePosition::getId);

        return positions.stream().sorted(order)
                .map(position -> mapToResponse(position, activeAssignments.get(position.getId())))
                .toList();
    }

    private CommitteePositionResponse mapToResponse(
            CommitteePosition position, AdvocateAssignment assignment) {

        Committee committee = position.getCommittee();
        OrganizationUnit responsibility = position.getResponsibilityUnit();
        AppUser advocate = assignment == null ? null : assignment.getAdvocate();
        boolean occupied = assignment != null;
        boolean available = position.isActive() && !occupied
                && getUnavailabilityReason(position) == null;

        return new CommitteePositionResponse(
                position.getId(), committee.getId(), committee.getName(),
                position.getPositionType(), position.getPositionNumber(),
                responsibility == null ? null : responsibility.getId(),
                responsibility == null ? null : responsibility.getName(),
                position.isActive(), occupied, available,
                advocate == null ? null : advocate.getId(),
                advocate == null ? null : advocate.getFullName(),
                position.getCreatedAt(), position.getUpdatedAt());
    }


    /**
     * This excludes the position's own active flag: activation must work on an
     * inactive position. It also excludes occupancy: occupancy is a separate fact.
     */
    private String getUnavailabilityReason(CommitteePosition position) {
        Committee committee = position.getCommittee();
        if (committee == null || !Boolean.TRUE.equals(committee.getActive())) {
            return "the committee is inactive or missing";
        }

        CommitteeType type = committee.getCommitteeType();
        OrganizationUnit unit = committee.getOrganizationUnit();
        if (type == null || unit == null || type.getOrganizationType() != unit.getType()) {
            return "the committee type does not match its organization unit";
        }

        if (!isHierarchyActive(unit)) {
            return "the organization hierarchy is inactive, invalid or contains a cycle";
        }

        Integer number = position.getPositionNumber();
        Integer count = position.getPositionType() == null ? null
                : type.getPositionTemplate().get(position.getPositionType());
        if (count == null || number == null || number < 1 || number > count) {
            return "the position type or number is outside the committee template";
        }

        boolean stateVicePresident =
                type == CommitteeType.STATE_COMMITTEE
                && position.getPositionType() == CommitteePositionType.VICE_PRESIDENT;

        OrganizationUnit responsibility = position.getResponsibilityUnit();

        if (stateVicePresident) {
            if (responsibility == null || responsibility.getType() != OrganizationType.DIVISION
                    || responsibility.getParent() == null
                    || !Objects.equals(responsibility.getParent().getId(), unit.getId())) {
                return "a state vice-president seat must refer to a division of its own state";
            }
            if (!isHierarchyActive(responsibility)) {
                return "the responsibility division or its hierarchy is inactive or invalid";
            }
        } else if (responsibility != null) {
            return "only a state vice-president seat can have a responsibility division";
        }

        return null;
    }


    private boolean isHierarchyActive(OrganizationUnit unit) {
        Set<Long> visited = new HashSet<>();
        OrganizationUnit current = unit;
        while (current != null) {
            if (current.getId() == null || !visited.add(current.getId())
                    || !Boolean.TRUE.equals(current.getActive()) || current.getType() == null) {
                return false;
            }
            OrganizationUnit parent = current.getParent();
            OrganizationType parentType = parent == null ? null : parent.getType();
            if (!current.getType().canHaveParent(parentType)) {
                return false;
            }
            current = parent;
        }
        return true;
    }

}
