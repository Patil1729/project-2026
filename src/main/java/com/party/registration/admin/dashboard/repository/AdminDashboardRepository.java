package com.party.registration.admin.dashboard.repository;

import com.party.registration.admin.common.dto.OrganizationScope;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.approval.entity.ApprovalStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

/** Aggregate queries keep dashboard totals independent of search pagination. */
@Repository
@RequiredArgsConstructor
public class AdminDashboardRepository {
    private final EntityManager entityManager;

    public Map<RegistrationStatus, Long> applicationCounts(OrganizationScope scope) {
        String jpql = "select a.status, count(a) from RegistrationApplication a where 1 = 1"
                + scopeClause(scope, "a.selectedShakha.id") + " group by a.status";
        var counts = new EnumMap<RegistrationStatus, Long>(RegistrationStatus.class);
        for (var status : RegistrationStatus.values()) counts.put(status, 0L);
        for (Object[] row : scoped(entityManager.createQuery(jpql, Object[].class), scope).getResultList())
            counts.put((RegistrationStatus) row[0], ((Number) row[1]).longValue());
        return counts;
    }

    public long openSteps(OrganizationScope scope, Instant now, boolean overdueOnly, boolean escalatedOnly) {
        String jpql = "select count(s) from ApprovalStep s where s.application.status = :applicationStatus"
                + " and s.status in :openStatuses"
                + scopeClause(scope, "s.application.selectedShakha.id")
                + (overdueOnly ? " and s.dueAt <= :now" : "")
                + (escalatedOnly ? " and s.escalatedAt is not null" : "");
        var query = scoped(entityManager.createQuery(jpql, Long.class), scope)
                .setParameter("applicationStatus", RegistrationStatus.UNDER_REVIEW)
                .setParameter("openStatuses", List.of(ApprovalStatus.PENDING, ApprovalStatus.OVERDUE));
        if (overdueOnly) query.setParameter("now", now);
        return query.getSingleResult();
    }

    public long activeCommittees(OrganizationScope scope) {
        return scoped(entityManager.createQuery("select count(c) from Committee c where c.active = true"
                + " and c.organizationUnit.active = true" + scopeClause(scope, "c.organizationUnit.id"), Long.class), scope)
                .getSingleResult();
    }

    public long activePositions(OrganizationScope scope, boolean occupiedOnly) {
        String jpql = "select count(p) from CommitteePosition p where p.active = true"
                + " and p.committee.active = true and p.committee.organizationUnit.active = true"
                + scopeClause(scope, "p.committee.organizationUnit.id")
                + (occupiedOnly ? " and exists (select a.id from AdvocateAssignment a where a.committeePosition = p and a.active = true)" : "");
        return scoped(entityManager.createQuery(jpql, Long.class), scope).getSingleResult();
    }

    private String scopeClause(OrganizationScope scope, String field) {
        return scope.isGlobal() ? "" : " and " + field + " in :unitIds";
    }

    private <T> TypedQuery<T> scoped(TypedQuery<T> query, OrganizationScope scope) {
        if (!scope.isGlobal()) query.setParameter("unitIds", scope.unitIds());
        return query;
    }
}
