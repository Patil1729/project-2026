package com.party.committee.entity;

import com.party.organization.entity.OrganizationType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public enum CommitteeType {

    STATE_COMMITTEE(OrganizationType.STATE),
    DIVISIONAL_COMMITTEE(OrganizationType.DIVISION),
    DISTRICT_COMMITTEE(OrganizationType.DISTRICT),
    DIVISIONAL_DISTRICT_COMMITTEE(OrganizationType.DIVISIONAL_DISTRICT),
    ASSEMBLY_COMMITTEE(OrganizationType.ASSEMBLY_CONSTITUENCY),
    TALUKA_COMMITTEE(OrganizationType.TALUKA),
    MUNICIPAL_CORPORATION_COMMITTEE(OrganizationType.MUNICIPAL_CORPORATION),
    MUNICIPAL_COUNCIL_COMMITTEE(OrganizationType.MUNICIPAL_COUNCIL),
    NAGAR_PANCHAYAT_COMMITTEE(OrganizationType.NAGAR_PANCHAYAT);

    private final OrganizationType organizationType;

    CommitteeType(OrganizationType organizationType) {
        this.organizationType = organizationType;
    }

    public OrganizationType getOrganizationType() {
        return organizationType;
    }

    /**
     * A position template, not automatic persistence.
     * The service creates one CommitteePosition per seat in a transaction.
     */
    public Map<CommitteePositionType, Integer> getPositionTemplate() {
        Map<CommitteePositionType, Integer> positions =
                new EnumMap<>(CommitteePositionType.class);

        positions.put(CommitteePositionType.PRESIDENT, 1);
        positions.put(CommitteePositionType.VICE_PRESIDENT,
                this == STATE_COMMITTEE ? 7 : 2);
        positions.put(CommitteePositionType.WORKING_PRESIDENT, 1);
        positions.put(CommitteePositionType.SECRETARY, 1);

        if (this != STATE_COMMITTEE) {
            positions.put(CommitteePositionType.JOINT_SECRETARY, 1);
        }

        positions.put(CommitteePositionType.TREASURER, 1);

        if (this != STATE_COMMITTEE) {
            positions.put(CommitteePositionType.DEPUTY_TREASURER, 1);
            positions.put(CommitteePositionType.MEMBER, 3);
        }

        return Collections.unmodifiableMap(positions);
    }
}

