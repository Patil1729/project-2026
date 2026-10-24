package com.party.registration.approval.policy;

import com.party.committee.entity.Committee;
import com.party.committee.entity.CommitteePositionType;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.approval.entity.ApprovalStatus;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.registration.common.exception.RegistrationException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Shared structural checks for decisions, escalation, and senior read access. */
public final class ApprovalChainPolicy {
    private ApprovalChainPolicy() { }

    public static ApprovalStep requireActiveStep(RegistrationApplication application, List<ApprovalStep> steps,
                                          List<OrganizationUnit> ancestry) {
        int start = application.getRequestedPosition().getPositionType() == CommitteePositionType.PRESIDENT ? 1 : 0;
        List<OrganizationUnit> required = ancestry.subList(start, ancestry.size()).stream()
                .filter(unit -> unit.getType() != OrganizationType.PARTY).toList();
        if (required.isEmpty() || required.size() != steps.size()) {
            throw invalidChain();
        }
        ApprovalStep active = null;
        for (int index = 0; index < steps.size(); index++) {
            ApprovalStep step = steps.get(index);
            Committee committee = step.getReviewerCommittee();
            if (!Objects.equals(step.getSequenceNumber(), index + 1) || step.getStatus() == null
                    || !Objects.equals(step.getApplication().getId(), application.getId())
                    || !Boolean.TRUE.equals(committee.getActive()) || committee.getCommitteeType() == null
                    || committee.getOrganizationUnit() == null
                    || !Objects.equals(committee.getOrganizationUnit().getId(), required.get(index).getId())
                    || committee.getCommitteeType().getOrganizationType() != required.get(index).getType()
                    || !step.isTimelineValid()) {
                throw invalidChain();
            }
            if (step.getStatus() == ApprovalStatus.APPROVED) {
                if (active != null) throw invalidChain();
            } else if (step.getStatus().canBeDecided()) {
                if (active != null) throw invalidChain();
                active = step;
            } else if (step.getStatus() != ApprovalStatus.WAITING || active == null) {
                throw invalidChain();
            }
        }
        if (active == null) throw invalidChain();
        return active;
    }

    public static List<OrganizationUnit> activeAncestry(OrganizationUnit selected) {
        List<OrganizationUnit> ancestry = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (OrganizationUnit unit = selected; unit != null; unit = unit.getParent()) {
            if (unit.getId() == null || !visited.add(unit.getId())) throw invalidOrganization();
            ancestry.add(unit);
        }
        validateAncestry(ancestry);
        return ancestry;
    }

    public static void validateAncestry(List<OrganizationUnit> ancestry) {
        if (ancestry.isEmpty()) throw invalidOrganization();
        for (int index = 0; index < ancestry.size(); index++) {
            OrganizationUnit unit = ancestry.get(index);
            OrganizationType parentType = index + 1 < ancestry.size() ? ancestry.get(index + 1).getType() : null;
            if (!Boolean.TRUE.equals(unit.getActive()) || unit.getType() == null || !unit.getType().canHaveParent(parentType)) {
                throw invalidOrganization();
            }
        }
    }

    private static RegistrationException invalidChain() {
        return new RegistrationException(HttpStatus.CONFLICT, "INVALID_APPROVAL_CHAIN",
                "The complete required chain, order, and timelines must be valid.");
    }
    private static RegistrationException invalidOrganization() {
        return new RegistrationException(HttpStatus.CONFLICT, "INVALID_ORGANIZATION",
                "The complete organization ancestry must remain active and valid.");
    }
}
