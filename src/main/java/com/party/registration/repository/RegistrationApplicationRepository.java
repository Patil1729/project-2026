package com.party.registration.repository;

import com.party.registration.entity.RegistrationApplication;
import com.party.registration.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface RegistrationApplicationRepository extends JpaRepository<RegistrationApplication, Long> {

    boolean existsByRequestedPosition_Committee_IdAndStatusIn(
            Long committeeId, Collection<RegistrationStatus> statuses);
}
