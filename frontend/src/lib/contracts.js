export const parentType = {
  PARTY: null,
  STATE: "PARTY",
  DIVISION: "STATE",
  DISTRICT: "DIVISION",
  DIVISIONAL_DISTRICT: "DISTRICT",
  ASSEMBLY_CONSTITUENCY: "DIVISIONAL_DISTRICT",
  TALUKA: "DIVISIONAL_DISTRICT",
  MUNICIPAL_CORPORATION: "DIVISIONAL_DISTRICT",
  MUNICIPAL_COUNCIL: "DIVISIONAL_DISTRICT",
  NAGAR_PANCHAYAT: "DIVISIONAL_DISTRICT",
};
export const committeeTypes = {
  STATE: "STATE_COMMITTEE",
  DIVISION: "DIVISIONAL_COMMITTEE",
  DISTRICT: "DISTRICT_COMMITTEE",
  DIVISIONAL_DISTRICT: "DIVISIONAL_DISTRICT_COMMITTEE",
  ASSEMBLY_CONSTITUENCY: "ASSEMBLY_COMMITTEE",
  TALUKA: "TALUKA_COMMITTEE",
  MUNICIPAL_CORPORATION: "MUNICIPAL_CORPORATION_COMMITTEE",
  MUNICIPAL_COUNCIL: "MUNICIPAL_COUNCIL_COMMITTEE",
  NAGAR_PANCHAYAT: "NAGAR_PANCHAYAT_COMMITTEE",
};
export const statuses = [
  "AWAITING_RECOMMENDATION",
  "UNDER_REVIEW",
  "APPROVED",
  "REJECTED",
];
export const documentTypes = [
  "IDENTITY_PROOF",
  "ADDRESS_PROOF",
  "EDUCATION_CERTIFICATE",
  "VOTER_ID",
  "AADHAAR_PROOF",
  "RECOMMENDATION_LETTER",
  "OTHER",
];
export const maxFileBytes = 5242880;
export function positionTemplate(committeeType) {
  const state = committeeType === "STATE_COMMITTEE";
  const counts = [
    ["PRESIDENT", 1],
    ["VICE_PRESIDENT", state ? 7 : 2],
    ["WORKING_PRESIDENT", 1],
    ["SECRETARY", 1],
    ...(!state ? [["JOINT_SECRETARY", 1]] : []),
    ["TREASURER", 1],
    ...(!state
      ? [
          ["DEPUTY_TREASURER", 1],
          ["MEMBER", 3],
        ]
      : []),
  ];
  return counts.flatMap(([positionType, count]) =>
    Array.from({ length: count }, (_, i) => ({
      positionType,
      positionNumber: i + 1,
    })),
  );
}
