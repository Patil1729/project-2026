package com.party.committee.repository;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.CommitteePositionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;

@Repository
public interface AdvocateAssignmentRepository extends JpaRepository<AdvocateAssignment, Long> {

    boolean existsByCommitteePosition_Committee_IdAndActiveTrue(Long committeeId);

    @Query("""
            select count(a) from AdvocateAssignment a
            join a.committeePosition p
            left join p.responsibilityUnit r
            where a.active = true
              and (p.committee.organizationUnit.id = :unitId or r.id = :unitId)
            """)
    long countActiveAssignmentsForOrganizationUnit(@Param("unitId") Long unitId);

    boolean existsByAdvocate_IdAndActiveTrue(Long userId);

    // Authority comes from a current appointment, never from a user's home unit.
    @Query("""
            select count(a) from AdvocateAssignment a
            join a.committeePosition p
            join p.committee c
            join c.organizationUnit u
            where a.advocate.id = :userId
              and a.active = true and a.endDate is null and a.appointedDate <= :today
              and p.active = true and p.positionType = :positionType
              and c.active = true and u.active = true
              and u.id in :unitIds
            """)
    long countCurrentPresidentsInUnits(
            @Param("userId") Long userId,
            @Param("unitIds") Collection<Long> unitIds,
            @Param("positionType") CommitteePositionType positionType,
            @Param("today") LocalDate today);
}
