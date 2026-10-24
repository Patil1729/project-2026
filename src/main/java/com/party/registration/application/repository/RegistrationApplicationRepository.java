package com.party.registration.application.repository;

import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationApplicationRepository extends JpaRepository<RegistrationApplication, Long> {

    // Existing safeguard methods retained from registration.zip.
    boolean existsByRequestedPosition_Committee_IdAndStatusIn(
            Long committeeId, Collection<RegistrationStatus> statuses);

    @Query("""
            select count(a) from RegistrationApplication a
            join a.requestedPosition p
            left join p.responsibilityUnit r
            where a.status in :statuses
              and (a.selectedShakha.id = :unitId
                   or p.committee.organizationUnit.id = :unitId
                   or r.id = :unitId)
            """)
    long countPendingApplicationsForOrganizationUnit(
            @Param("unitId") Long unitId,
            @Param("statuses") Collection<RegistrationStatus> statuses);

    boolean existsByApplicant_IdAndStatusIn(Long userId, Collection<RegistrationStatus> statuses);

    boolean existsByApplicant_IdAndRequestedPosition_IdAndStatusIn(
            Long applicantId, Long positionId, Collection<RegistrationStatus> statuses);

    @EntityGraph(attributePaths = {
            "applicant", "selectedShakha", "requestedPosition", "requestedPosition.committee"
    })
    Optional<RegistrationApplication> findByIdAndApplicant_Id(Long id, Long applicantId);

    @EntityGraph(attributePaths = {
            "applicant", "selectedShakha", "requestedPosition", "requestedPosition.committee"
    })
    List<RegistrationApplication> findAllByApplicant_IdOrderByIdDesc(Long applicantId);

    // Serializes document versions. Submission must use this same application lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RegistrationApplication a where a.id = :id and a.applicant.id = :applicantId")
    Optional<RegistrationApplication> findOwnedByIdForUpdate(
            @Param("id") Long id, @Param("applicantId") Long applicantId);

    // Review and upload lock the same parent row before reading document versions.
    // This query is not ownership-filtered: the review service checks appointment scope.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RegistrationApplication a where a.id = :id")
    Optional<RegistrationApplication> findByIdForDocumentReviewUpdate(@Param("id") Long id);

    // A scalar authorization lookup avoids hydrating the unlocked applicant/application.
    @Query("""
            select a.applicant.id from RegistrationApplication a
            where a.id = :applicationId and exists (
                select s.id from ApprovalStep s
                where s.application = a and s.id = :stepId and s.assignedReviewer.id = :reviewerId
            )
            """)
    Optional<Long> findApplicantIdForAssignedStep(
            @Param("applicationId") Long applicationId, @Param("stepId") Long stepId,
            @Param("reviewerId") Long reviewerId);

    // Same parent-row lock used by upload, document review, and submission.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RegistrationApplication a where a.id = :id")
    Optional<RegistrationApplication> findByIdForApprovalUpdate(@Param("id") Long id);
}
