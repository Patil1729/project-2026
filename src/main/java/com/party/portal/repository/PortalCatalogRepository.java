package com.party.portal.repository;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.CommitteePosition;
import com.party.committee.entity.CommitteeType;
import com.party.organization.entity.OrganizationUnit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface PortalCatalogRepository extends Repository<CommitteePosition, Long> {
    @Query("select u from OrganizationUnit u left join fetch u.parent where u.active = true order by u.name, u.id")
    List<OrganizationUnit> findActiveOrganizations();

    @Query("""
            select p from CommitteePosition p
            join fetch p.committee c join fetch c.organizationUnit u
            left join fetch p.responsibilityUnit
            where p.active = true and c.active = true and u.active = true and c.committeeType = :type
            order by c.id, p.positionType, p.positionNumber
            """)
    List<CommitteePosition> findStatePositions(@Param("type") CommitteeType type);

    @Query("""
            select a from AdvocateAssignment a
            join fetch a.advocate join fetch a.committeePosition p join fetch p.committee c
            where a.active = true and a.endDate is null and a.appointedDate <= :today
              and c.committeeType = :type
            """)
    List<AdvocateAssignment> findCurrentStateAssignments(@Param("type") CommitteeType type, @Param("today") LocalDate today);
}
