package com.party.committee.repository;

import com.party.committee.entity.Committee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitteeRepository extends JpaRepository<Committee, Long> {


    boolean existsByCode( String code);

    boolean existsByOrganizationUnitId(Long id);
}
