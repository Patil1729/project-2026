package com.party.user.entity;

/**
 * Permissions, separate from committee titles.
 * Check organization scope as well as role.
 * Existing role names are retained for stored-data compatibility.
 */
public enum RoleType {
    APPLICANT,
    PARTY_HEAD,
    STATE_ADMIN,
    DIVISION_ADMIN,
    DISTRICT_ADMIN,
    DIVISIONAL_DISTRICT_ADMIN,
    CONSTITUENCY_ADMIN,
    TALUKA_ADMIN,
    MUNICIPAL_CORPORATION_ADMIN,
    MUNICIPAL_COUNCIL_ADMIN,
    NAGAR_PANCHAYAT_ADMIN,
    ADVOCATE,
    WARD_ADMIN, // Legacy permission; no WARD committee in the agreed hierarchy.
    FIELD_WORKER
}
