package com.party.registration.repository;

import com.party.registration.entity.ApprovalStatus;
import com.party.registration.entity.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    @Query("""
            select count(s) from ApprovalStep s
            left join s.assignedReviewer reviewer
            left join s.escalatedTo senior
            where s.status in :statuses
              and (reviewer.id = :userId or senior.id = :userId)
            """)
    long countOpenResponsibilities(
            @Param("userId") Long userId,
            @Param("statuses") Collection<ApprovalStatus> statuses);
}
