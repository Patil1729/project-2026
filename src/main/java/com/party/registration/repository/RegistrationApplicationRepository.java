package com.party.registration.repository;

import com.party.registration.entity.RegistrationApplication;
import com.party.registration.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface RegistrationApplicationRepository extends JpaRepository<RegistrationApplication, Long> {

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
}
