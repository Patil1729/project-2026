package com.party.registration.admin.search.repository;

import com.party.registration.application.entity.RegistrationApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface AdminApplicationRepository extends JpaRepository<RegistrationApplication, Long>,
        JpaSpecificationExecutor<RegistrationApplication> {
    @Override
    @EntityGraph(attributePaths = {"applicant", "selectedShakha", "requestedPosition",
            "requestedPosition.committee", "assignment"})
    Page<RegistrationApplication> findAll(Specification<RegistrationApplication> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"applicant", "selectedShakha", "requestedPosition",
            "requestedPosition.committee", "assignment"})
    Optional<RegistrationApplication> findById(Long id);
}
