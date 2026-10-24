package com.party.registration.approval.repository;

import com.party.registration.approval.entity.ApprovalStatus;
import com.party.registration.escalation.dto.ApprovalEscalationCandidate;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.committee.entity.CommitteePositionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    boolean existsByApplication_Id(Long applicationId);

    @EntityGraph(attributePaths = {
            "reviewerCommittee", "reviewerCommittee.organizationUnit",
            "assignedReviewer", "decidedBy", "escalatedTo"
    })
    List<ApprovalStep> findAllByApplication_IdOrderBySequenceNumberAsc(Long applicationId);

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

    // No fetch graph here: mutation locks committees/units before reading their state.
    @Query("select s from ApprovalStep s where s.application.id = :applicationId order by s.sequenceNumber")
    List<ApprovalStep> findForDecisionByApplicationId(@Param("applicationId") Long applicationId);

    @EntityGraph(attributePaths = {
            "application", "application.applicant", "application.selectedShakha",
            "application.requestedPosition", "application.requestedPosition.committee",
            "reviewerCommittee", "reviewerCommittee.organizationUnit", "assignedReviewer"
    })
    @Query("""
            select s from ApprovalStep s
            where s.assignedReviewer.id = :reviewerId
              and s.application.applicant.id <> :reviewerId
              and s.application.status = :applicationStatus and s.status in :statuses
              and exists (
                  select a.id from AdvocateAssignment a
                  join a.committeePosition p join p.committee c
                  where a.advocate.id = :reviewerId and a.advocate.enabled = true
                    and a.active = true and a.endDate is null and a.appointedDate <= :today
                    and p.active = true and p.positionType = :president and p.positionNumber = 1
                    and c = s.reviewerCommittee and c.active = true and c.organizationUnit.active = true
              )
            order by s.dueAt, s.id
            """)
    Slice<ApprovalStep> findPendingInbox(
            @Param("reviewerId") Long reviewerId, @Param("applicationStatus") RegistrationStatus applicationStatus,
            @Param("statuses") Collection<ApprovalStatus> statuses,
            @Param("president") CommitteePositionType president,
            @Param("today") java.time.LocalDate today, Pageable pageable);

    // Keyset pagination avoids offset drift as rows leave the candidate set.
    @Query("""
            select new com.party.registration.escalation.dto.ApprovalEscalationCandidate(s.id, s.application.id)
            from ApprovalStep s
            where s.id > :afterId and s.application.status = :applicationStatus
              and s.status in :statuses and s.dueAt <= :cutoff and s.escalatedAt is null
            order by s.id
            """)
    List<ApprovalEscalationCandidate> findEscalationCandidates(
            @Param("cutoff") java.time.Instant cutoff, @Param("afterId") long afterId,
            @Param("applicationStatus") RegistrationStatus applicationStatus,
            @Param("statuses") Collection<ApprovalStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {
            "application", "application.applicant", "application.selectedShakha",
            "application.requestedPosition", "application.requestedPosition.committee",
            "reviewerCommittee", "reviewerCommittee.organizationUnit", "assignedReviewer", "escalatedTo"
    })
    @Query("""
            select s from ApprovalStep s
            where s.escalatedTo.id = :reviewerId and s.escalatedAt is not null
              and s.application.applicant.id <> :reviewerId
              and s.application.status = :applicationStatus and s.status in :statuses
              and exists (
                  select a.id from AdvocateAssignment a
                  join a.committeePosition p join p.committee c
                  where a.advocate.id = :reviewerId and a.advocate.enabled = true
                    and a.active = true and a.endDate is null and a.appointedDate <= :today
                    and p.active = true and p.positionType = :president and p.positionNumber = 1
                    and c.active = true and c.organizationUnit.active = true
                    and exists (
                        select nextStep.id from ApprovalStep nextStep
                        where nextStep.application = s.application
                          and nextStep.sequenceNumber = s.sequenceNumber + 1 and nextStep.reviewerCommittee = c
                    )
              )
            order by s.dueAt, s.id
            """)
    Slice<ApprovalStep> findEscalatedInbox(
            @Param("reviewerId") Long reviewerId, @Param("applicationStatus") RegistrationStatus applicationStatus,
            @Param("statuses") Collection<ApprovalStatus> statuses, @Param("president") CommitteePositionType president,
            @Param("today") java.time.LocalDate today, Pageable pageable);
}
