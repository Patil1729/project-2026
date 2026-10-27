package com.party.registration.admin.search.repository;

import com.party.registration.admin.common.dto.OrganizationScope;
import com.party.registration.admin.search.dto.ApplicationSearchRequest;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.approval.policy.ApprovalPolicy;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.time.ZoneOffset;
import java.util.ArrayList;

public final class ApplicationSearchSpecifications {
    private ApplicationSearchSpecifications() { }

    public static Specification<RegistrationApplication> matching(ApplicationSearchRequest request, OrganizationScope scope) {
        return (root, query, cb) -> {
            var filters = new ArrayList<Predicate>();
            if (request.getStatus() != null) filters.add(cb.equal(root.get("status"), request.getStatus()));
            if (request.getApplicantId() != null) filters.add(cb.equal(root.get("applicant").get("id"), request.getApplicantId()));
            if (request.getCommitteeId() != null) filters.add(cb.equal(root.get("requestedPosition").get("committee").get("id"), request.getCommitteeId()));
            if (!scope.isGlobal()) filters.add(root.get("selectedShakha").get("id").in(scope.unitIds()));
            if (request.getCreatedFrom() != null) filters.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                    request.getCreatedFrom().atStartOfDay(ApprovalPolicy.BUSINESS_ZONE)
                            .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            if (request.getCreatedTo() != null) filters.add(cb.lessThan(root.get("createdAt"),
                    request.getCreatedTo().plusDays(1).atStartOfDay(ApprovalPolicy.BUSINESS_ZONE)
                            .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            if (request.getQ() != null && !request.getQ().isBlank()) {
                String pattern = LiteralSearchPattern.contains(request.getQ());
                filters.add(cb.or(cb.like(cb.lower(root.get("applicationNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("applicant").get("fullName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("applicant").get("username")), pattern, '\\'),
                        cb.like(root.get("applicant").get("mobile"), pattern, '\\')));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

}
