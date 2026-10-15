package com.party.committee.repository;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.CommitteePosition;
import com.party.registration.entity.RegistrationStatus;
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
public interface CommitteePositionRepository extends JpaRepository<CommitteePosition, Long> {

    @EntityGraph(attributePaths = {
            "committee", "committee.organizationUnit", "responsibilityUnit"
    })
    @Query("""
            select p from CommitteePosition p
            where p.committee.id = :committeeId
            order by p.positionType, p.positionNumber, p.id
            """)
    List<CommitteePosition> findPositionsByCommitteeId(
            @Param("committeeId") Long committeeId);

    @EntityGraph(attributePaths = {
            "committee", "committee.organizationUnit", "responsibilityUnit"
    })
    @Query("select p from CommitteePosition p where p.id = :id")
    Optional<CommitteePosition> findWithDetailsById(@Param("id") Long id);

    // A scalar lookup avoids loading an unlocked position into the persistence context.
    @Query("select p.committee.id from CommitteePosition p where p.id = :id")
    Optional<Long> findCommitteeIdByPositionId(@Param("id") Long id);

    // Lock the committee first in the service, then this position.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CommitteePosition p where p.id = :id")
    Optional<CommitteePosition> findByIdForUpdate(@Param("id") Long id);

    // One batch query for occupancy, rather than one assignment query per seat.
    @Query("""
            select a from AdvocateAssignment a
            join fetch a.advocate
            where a.committeePosition.id in :positionIds and a.active = true
            """)
    List<AdvocateAssignment> findActiveAssignmentsForPositions(
            @Param("positionIds") Collection<Long> positionIds);

    @Query("""
            select count(a) from AdvocateAssignment a
            where a.committeePosition.id = :positionId and a.active = true
            """)
    long countActiveAssignmentsForPositionId(
            @Param("positionId") Long positionId);

    @Query("""
            select count(a) from RegistrationApplication a
            where a.requestedPosition.id = :positionId and a.status in :statuses
            """)
    long countApplicationsForPositionIdAndStatuses(
            @Param("positionId") Long positionId,
            @Param("statuses") Collection<RegistrationStatus> statuses);
}


