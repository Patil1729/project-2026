package com.party.committee.repository;

import com.party.committee.entity.AdvocateAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
