package com.party.committee.repository;

import com.party.committee.entity.CommitteePosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitteePositionRepository extends JpaRepository<CommitteePosition, Long> {
}
