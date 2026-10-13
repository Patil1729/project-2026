package com.party.committee.repository;

import com.party.committee.entity.AdvocateAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdvocateAssignmentRepository extends JpaRepository<AdvocateAssignment, Long> {

    boolean existsByCommitteePosition_Committee_IdAndActiveTrue(Long committeeId);
}
