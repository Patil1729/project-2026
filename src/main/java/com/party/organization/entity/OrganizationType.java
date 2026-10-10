package com.party.organization.entity;

/**
 * Units in the Legal Cell reporting hierarchy, not government jurisdiction boundaries.
 * Stored as enum names in organization_units.type.
 */
public enum OrganizationType {

    PARTY,
    STATE,
    DIVISION,
    DISTRICT,
    DIVISIONAL_DISTRICT,
    ASSEMBLY_CONSTITUENCY,
    TALUKA,
    MUNICIPAL_CORPORATION,
    MUNICIPAL_COUNCIL,
    NAGAR_PANCHAYAT;

    public OrganizationType getRequiredParentType() {
        return switch (this) {
            case PARTY -> null;
            case STATE -> PARTY;
            case DIVISION -> STATE;
            case DISTRICT -> DIVISION;
            case DIVISIONAL_DISTRICT -> DISTRICT;
            case ASSEMBLY_CONSTITUENCY, TALUKA, MUNICIPAL_CORPORATION,
                 MUNICIPAL_COUNCIL, NAGAR_PANCHAYAT -> DIVISIONAL_DISTRICT;
        };
    }

    /**
     * Call from the service after resolving the parent.
     * PARTY accepts a null parent; every other type needs its required parent.
     */
    public boolean canHaveParent(OrganizationType parentType) {
        return getRequiredParentType() == parentType;
    }
}

