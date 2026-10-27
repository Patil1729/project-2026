package com.party.committee.appointment.repository;

import com.party.committee.appointment.dto.AppointmentReference;
import com.party.committee.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** Management queries; the existing AdvocateAssignmentRepository remains available to registration. */
public interface AppointmentRepository extends JpaRepository<AdvocateAssignment, Long>, JpaSpecificationExecutor<AdvocateAssignment> {
    @Override
    @EntityGraph(attributePaths = {"advocate", "committeePosition", "committeePosition.committee",
            "committeePosition.committee.organizationUnit", "registrationApplication"})
    Page<AdvocateAssignment> findAll(Specification<AdvocateAssignment> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"advocate", "committeePosition", "committeePosition.committee",
            "committeePosition.committee.organizationUnit", "registrationApplication"})
    Optional<AdvocateAssignment> findById(Long id);

    @Query("select new com.party.committee.appointment.dto.AppointmentReference(a.advocate.id, a.committeePosition.id, a.committeePosition.committee.id) "
            + "from AdvocateAssignment a where a.id = :id")
    Optional<AppointmentReference> findReference(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdvocateAssignment a where a.id = :id")
    Optional<AdvocateAssignment> findForUpdate(@Param("id") Long id);

    @Query("select p.id from CommitteePosition p where p.committee.id = :committeeId "
            + "and p.positionType = :president and p.positionNumber = 1")
    Optional<Long> findPresidentSeatId(@Param("committeeId") Long committeeId, @Param("president") CommitteePositionType president);
}
