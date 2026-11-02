package com.party.portal.repository;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.CommitteeType;
import com.party.user.entity.RoleType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface PortalRepository extends Repository<AdvocateAssignment, Long> {
    @Query("select count(distinct u.id) from AppUser u join u.roles r where r.name = :role")
    long countRegisteredAdvocates(@Param("role") RoleType role);

    @Query("""
            select a from AdvocateAssignment a
            join fetch a.advocate advocate
            join fetch a.committeePosition p
            join fetch p.committee c
            join fetch c.organizationUnit u
            left join fetch p.responsibilityUnit responsibility
            where a.active = true and a.endDate is null and a.appointedDate <= :today
              and advocate.enabled = true and p.active = true and c.active = true and u.active = true
              and c.committeeType = :type
            order by c.id, p.id
            """)
    List<AdvocateAssignment> findStateExecutive(@Param("type") CommitteeType type, @Param("today") LocalDate today);
}
